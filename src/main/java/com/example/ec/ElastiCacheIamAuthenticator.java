package com.example.ec;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.DefaultRequest;
import com.amazonaws.Request;
import com.amazonaws.SignableRequest;
import com.amazonaws.http.HttpMethodName;
import redis.clients.jedis.JedisClientConfig;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

/**
 * Utility class for IAM authentication with ElastiCache Serverless
 */
public class ElastiCacheIamAuthenticator implements JedisClientConfig {
    private static final Logger logger = LoggerFactory.getLogger(ElastiCacheIamAuthenticator.class);
    
    private static final HttpMethodName REQUEST_METHOD = HttpMethodName.GET;
    private static final String REQUEST_PROTOCOL = "http://";
    private static final String PARAM_ACTION = "Action";
    private static final String PARAM_USER = "User";
    private static final String PARAM_RESOURCE_TYPE = "ResourceType";
    private static final String RESOURCE_TYPE_SERVERLESS_CACHE = "ServerlessCache";
    private static final String ACTION_NAME = "connect";
    private static final String SERVICE_NAME = "elasticache";
    private static final long TOKEN_EXPIRY_SECONDS = 900; // 15 minutes
    
    private final String userId;
    private final String cacheName;
    private final String region;
    private final boolean isServerless;
    private final AWSCredentialsProvider credentialsProvider;
    
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final boolean useSSL;
    private final HostnameVerifier hostnameVerifier;
    private final SSLSocketFactory sslSocketFactory;
    private final SSLParameters sslParameters;
    private final String host;
    private final int port;

    /**
     * Creates a new ElastiCacheIamAuthenticator.
     *
     * @param userId The username/user id for IAM authentication
     * @param region The AWS region
     * @param host The ElastiCache endpoint
     * @param port The ElastiCache port
     */
    public ElastiCacheIamAuthenticator(String userId, String region, String host, int port) {
        this(userId, region, host, port, 
             DefaultAWSCredentialsProviderChain.getInstance(),
             Duration.ofSeconds(10), 
             Duration.ofSeconds(10),
             true, null, null, null);
    }

    /**
     * Creates a new ElastiCacheIamAuthenticator with custom parameters.
     *
     * @param userId The user id for IAM authentication
     * @param region The AWS region
     * @param host The ElastiCache endpoint
     * @param port The ElastiCache port
     * @param credentialsProvider The AWS credentials provider
     * @param connectTimeout Connection timeout
     * @param readTimeout Read timeout
     * @param useSSL Whether to use SSL
     * @param hostnameVerifier The hostname verifier (optional)
     * @param sslSocketFactory The SSL socket factory (optional)
     * @param sslParameters The SSL parameters (optional)
     */
    public ElastiCacheIamAuthenticator(String userId, String region, String host, int port,
                                      AWSCredentialsProvider credentialsProvider,
                                      Duration connectTimeout, Duration readTimeout,
                                      boolean useSSL, HostnameVerifier hostnameVerifier,
                                      SSLSocketFactory sslSocketFactory, SSLParameters sslParameters) {
        this.userId = userId;
        this.region = region;
        this.host = host;
        this.port = port;
        this.credentialsProvider = credentialsProvider;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.useSSL = useSSL;
        this.hostnameVerifier = hostnameVerifier;
        this.sslSocketFactory = sslSocketFactory;
        this.sslParameters = sslParameters;
        
        // Log the host for debugging
        logger.info("Extracting cache name from host: {}", host);
        
        // Extract the cache name from the host name
        // For serverless caches, the cacheName is the first part of the host
        // e.g. cache-01-vk-yiy6se.serverless.euw1.cache.amazonaws.com -> cache-01-vk
        String[] parts = host.split("-");
        String extractedCacheName = null;
        if (parts.length >= 3) {
            // Find the cache name format like "cache-01-vk"
            extractedCacheName = parts[0] + "-" + parts[1] + "-" + parts[2].split("\\.")[0];
        } else {
            // Fallback to first part of host if unable to parse
            extractedCacheName = host.split("\\.")[0];
        }
        
        // Log the extracted cache name
        logger.info("Extracted cache name: {}", extractedCacheName);
        this.cacheName = extractedCacheName;
        
        // Determine if this is a serverless cache by looking at the endpoint
        this.isServerless = host.contains("serverless.");
        
        logger.debug("Initialized ElastiCacheIamAuthenticator for cache: {}, userId: {}, isServerless: {}", 
                    cacheName, userId, isServerless);
    }

    /**
     * Generates an IAM auth token for ElastiCache
     * Based on the AWS documentation for ElastiCache IAM authentication
     * 
     * @return The auth token to use with AUTH command
     */
    public String getPassword() {
        try {
            logger.debug("Generating IAM auth token for cache: {}, user: {}", cacheName, userId);
            
            // Get AWS credentials
            AWSCredentials credentials = credentialsProvider.getCredentials();
            logger.debug("Using credentials for access key: {}", 
                credentials.getAWSAccessKeyId().substring(0, 5) + "...");
            
            // Generate a signed request URI (token)
            String iamAuthToken = toSignedRequestUri(credentials);
            
            // Log the full token for debugging
            logger.info("Generated full IAM auth token: {}", iamAuthToken);
            
            return iamAuthToken;
        } catch (Exception e) {
            logger.error("Failed to generate IAM auth token", e);
            throw new RuntimeException("Failed to generate IAM auth token", e);
        }
    }
    
    /**
     * Creates a signable request and returns a signed URI.
     * 
     * @param credentials AWS credentials
     * @return Signed request URI to use as auth token
     */
    private String toSignedRequestUri(AWSCredentials credentials) throws URISyntaxException {
        Request<Void> request = getSignableRequest();
        sign(request, credentials);
        return new URIBuilder(request.getEndpoint())
            .addParameters(toNamedValuePair(request.getParameters()))
            .build()
            .toString()
            .replace(REQUEST_PROTOCOL, "");
    }
    
    /**
     * Creates a request that can be signed.
     * 
     * @return Request object ready for signing
     */
    private <T> Request<T> getSignableRequest() {
        Request<T> request = new DefaultRequest<>(SERVICE_NAME);
        request.setHttpMethod(REQUEST_METHOD);
        request.setEndpoint(getRequestUri());
        request.addParameter(PARAM_ACTION, ACTION_NAME);
        request.addParameter(PARAM_USER, userId);
        
        if (isServerless) {
            request.addParameter(PARAM_RESOURCE_TYPE, RESOURCE_TYPE_SERVERLESS_CACHE);
        }
        return request;
    }
    
    /**
     * Gets the request URI for the ElastiCache operation.
     * 
     * @return URI for the request
     */
    private URI getRequestUri() {
        return URI.create(String.format("%s%s/", REQUEST_PROTOCOL, cacheName));
    }
    
    /**
     * Signs the request using AWS SigV4.
     * 
     * @param request Request to sign
     * @param credentials AWS credentials to use for signing
     */
    private <T> void sign(SignableRequest<T> request, AWSCredentials credentials) {
        AWS4Signer signer = new AWS4Signer();
        signer.setRegionName(region);
        signer.setServiceName(SERVICE_NAME);
        
        // Set expiry time to 15 minutes from now
        Date expiryTime = new Date(System.currentTimeMillis() + TOKEN_EXPIRY_SECONDS * 1000);
        
        // Pre-sign the request
        signer.presignRequest(request, credentials, expiryTime);
    }
    
    /**
     * Converts a map of parameters to a list of name-value pairs.
     * 
     * @param in Map of parameters
     * @return List of name-value pairs
     */
    private static List<NameValuePair> toNamedValuePair(Map<String, List<String>> in) {
        return in.entrySet().stream()
            .map(e -> new BasicNameValuePair(e.getKey(), e.getValue().get(0)))
            .collect(Collectors.toList());
    }
    
    // JedisClientConfig implementation
    @Override
    public String getUser() {
        return userId;
    }

    @Override
    public int getConnectionTimeoutMillis() {
        return (int) connectTimeout.toMillis();
    }

    @Override
    public int getSocketTimeoutMillis() {
        return (int) readTimeout.toMillis();
    }

    @Override
    public boolean isSsl() {
        return useSSL;
    }

    @Override
    public SSLSocketFactory getSslSocketFactory() {
        return sslSocketFactory;
    }

    @Override
    public SSLParameters getSslParameters() {
        return sslParameters;
    }

    @Override
    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }
} 