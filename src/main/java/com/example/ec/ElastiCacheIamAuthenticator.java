package com.example.ec;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.DefaultRequest;
import com.amazonaws.Request;
import com.amazonaws.http.HttpMethodName;
import redis.clients.jedis.JedisClientConfig;

import java.net.URI;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;
import org.apache.http.client.utils.URIBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for IAM authentication with ElastiCache Serverless
 */
public class ElastiCacheIamAuthenticator implements JedisClientConfig {
    private static final Logger logger = LoggerFactory.getLogger(ElastiCacheIamAuthenticator.class);
    private static final String SERVICE_NAME = "elasticache";
    private static final HttpMethodName REQUEST_METHOD = HttpMethodName.GET;
    private static final String REQUEST_PROTOCOL = "http://";
    private static final String PARAM_ACTION = "Action";
    private static final String PARAM_USER = "User";
    private static final String PARAM_RESOURCE_TYPE = "ResourceType";
    private static final String RESOURCE_TYPE_SERVERLESS_CACHE = "ServerlessCache";
    private static final String ACTION_NAME = "connect";
    private static final long TOKEN_EXPIRY_SECONDS = 900; // 15 minutes
    
    private final String username;
    private final AWSCredentialsProvider credentialsProvider;
    private final String region;
    private final String host;
    private final int port;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final boolean useSSL;
    private final HostnameVerifier hostnameVerifier;
    private final SSLSocketFactory sslSocketFactory;
    private final SSLParameters sslParameters;
    private final boolean isServerless;

    /**
     * Creates a new ElastiCacheIamAuthenticator.
     *
     * @param username The username for IAM authentication
     * @param region The AWS region
     * @param host The ElastiCache endpoint
     * @param port The ElastiCache port
     */
    public ElastiCacheIamAuthenticator(String username, String region, String host, int port) {
        this(username, region, host, port, 
             DefaultAWSCredentialsProviderChain.getInstance(),
             Duration.ofSeconds(2), 
             Duration.ofSeconds(2),
             true, null, null, null);
    }

    /**
     * Creates a new ElastiCacheIamAuthenticator with custom parameters.
     *
     * @param username The username for IAM authentication
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
    public ElastiCacheIamAuthenticator(String username, String region, String host, int port,
                                      AWSCredentialsProvider credentialsProvider,
                                      Duration connectTimeout, Duration readTimeout,
                                      boolean useSSL, HostnameVerifier hostnameVerifier,
                                      SSLSocketFactory sslSocketFactory, SSLParameters sslParameters) {
        this.username = username;
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
        
        // Determine if this is a serverless cache by looking at the endpoint
        this.isServerless = host.contains("serverless.");
    }

    /**
     * Generates an IAM auth token for ElastiCache Serverless
     * Based on the AWS documentation for ElastiCache IAM authentication
     *
     * @return The auth token to use with AUTH command
     */
    public String getPassword() {
        try {
            // Extract the cache name from the host
            String cacheName = extractCacheName(host);
            logger.debug("Using credentials for access key: {}", 
                credentialsProvider.getCredentials().getAWSAccessKeyId().substring(0, 5) + "...");
            
            // Create a signable request
            DefaultRequest<Void> request = createSignableRequest(cacheName);
            
            // Sign the request using AWS4 signature
            signRequest(request, credentialsProvider.getCredentials());
            
            // Convert the request to a pre-signed URL which serves as the token
            String iamAuthToken = toSignedRequestUri(request);
            
            logger.debug("Generated ElastiCache IAM auth token successfully");
            
            return iamAuthToken;
        } catch (Exception e) {
            logger.error("Failed to generate IAM auth token", e);
            throw new RuntimeException("Failed to generate IAM auth token", e);
        }
    }
    
    private String extractCacheName(String host) {
        // Extract the cache name from the host name
        // e.g. cache-01-vk-yiy6se.serverless.euw1.cache.amazonaws.com -> cache-01-vk
        String[] parts = host.split("\\.");
        return parts[0];
    }
    
    private DefaultRequest<Void> createSignableRequest(String cacheName) {
        DefaultRequest<Void> request = new DefaultRequest<>(SERVICE_NAME);
        request.setHttpMethod(REQUEST_METHOD);
        request.setEndpoint(URI.create(REQUEST_PROTOCOL + cacheName + "/"));
        
        // Add required parameters
        request.addParameter(PARAM_ACTION, ACTION_NAME);
        request.addParameter(PARAM_USER, username);
        
        // Add serverless-specific parameter if this is a serverless cache
        if (isServerless) {
            request.addParameter(PARAM_RESOURCE_TYPE, RESOURCE_TYPE_SERVERLESS_CACHE);
        }
        
        return request;
    }
    
    private void signRequest(DefaultRequest<Void> request, AWSCredentials credentials) {
        AWS4Signer signer = new AWS4Signer();
        signer.setRegionName(region);
        signer.setServiceName(SERVICE_NAME);
        
        // Set expiry time to 15 minutes from now
        Date expiryTime = new Date(System.currentTimeMillis() + TOKEN_EXPIRY_SECONDS * 1000);
        
        // Pre-sign the request
        signer.presignRequest(request, credentials, expiryTime);
    }
    
    private String toSignedRequestUri(Request<Void> request) throws Exception {
        // Convert the parameters to a URI query string
        URI uri = request.getEndpoint();
        URIBuilder uriBuilder = new URIBuilder(uri);
        
        // Add all parameters from the request
        for (Map.Entry<String, List<String>> entry : request.getParameters().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().get(0);
            uriBuilder.addParameter(key, value);
        }
        
        // Build the URI and remove the protocol prefix
        return uriBuilder.build().toString().replaceFirst(REQUEST_PROTOCOL, "");
    }
    
    // JedisClientConfig implementation
    @Override
    public String getUser() {
        return username;
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