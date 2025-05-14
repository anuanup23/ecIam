package com.example.ec;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.http.HttpMethodName;
import com.amazonaws.ReadLimitInfo;
import redis.clients.jedis.JedisClientConfig;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.io.InputStream;
import java.time.Duration;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for IAM authentication with ElastiCache Serverless
 */
public class ElastiCacheIamAuthenticator implements JedisClientConfig {
    private static final Logger logger = LoggerFactory.getLogger(ElastiCacheIamAuthenticator.class);
    private static final String SERVICE_NAME = "elasticache";
    
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
    }

    /**
     * Generates an IAM auth token for ElastiCache Serverless
     *
     * @return The auth token to use with AUTH command
     */
    public String getPassword() {
        try {
            AWS4Signer signer = new AWS4Signer();
            signer.setServiceName(SERVICE_NAME);
            signer.setRegionName(region);
            
            URI uri = new URI("https://" + host + ":" + port);
            
            Map<String, String> headers = new HashMap<>();
            headers.put("host", host + ":" + port);
            
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("Action", "connect");
            queryParams.put("User", username);
            
            SigV4Request request = new SigV4Request(uri, HttpMethodName.GET, queryParams, headers, "");
            signer.sign(request, credentialsProvider.getCredentials());
            
            StringBuilder tokenBuilder = new StringBuilder();
            for (Map.Entry<String, String> param : queryParams.entrySet()) {
                if (tokenBuilder.length() > 0) {
                    tokenBuilder.append("&");
                }
                tokenBuilder.append(param.getKey()).append("=").append(param.getValue());
            }
            
            return tokenBuilder.toString();
        } catch (Exception e) {
            logger.error("Failed to generate IAM auth token", e);
            throw new RuntimeException("Failed to generate IAM auth token", e);
        }
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

    /**
     * Helper class to represent a request for AWS SigV4 signing
     */
    private static class SigV4Request implements com.amazonaws.SignableRequest<Void> {
        private final URI uri;
        private final HttpMethodName method;
        private final Map<String, String> queryParams;
        private final Map<String, String> headers;
        private final String bodyContent;

        public SigV4Request(URI uri, HttpMethodName method, Map<String, String> queryParams,
                          Map<String, String> headers, String bodyContent) {
            this.uri = uri;
            this.method = method;
            this.queryParams = queryParams;
            this.headers = headers;
            this.bodyContent = bodyContent;
        }

        @Override
        public Map<String, String> getHeaders() {
            return headers;
        }

        @Override
        public String getResourcePath() {
            return uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
        }

        @Override
        public void addHeader(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public void addParameter(String name, String value) {
            queryParams.put(name, value);
        }

        @Override
        public URI getEndpoint() {
            return uri;
        }

        @Override
        public HttpMethodName getHttpMethod() {
            return method;
        }

        @Override
        public Map<String, List<String>> getParameters() {
            Map<String, List<String>> result = new HashMap<>();
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                result.put(entry.getKey(), Collections.singletonList(entry.getValue()));
            }
            return result;
        }

        @Override
        public java.io.InputStream getContent() {
            if (bodyContent == null || bodyContent.isEmpty()) {
                return null;
            }
            return new java.io.ByteArrayInputStream(bodyContent.getBytes());
        }

        @Override
        public Void getOriginalRequestObject() {
            return null;
        }

        @Override
        public void setContent(java.io.InputStream content) {
            // Not implemented for this example
        }

        @Override
        public ReadLimitInfo getReadLimitInfo() {
            return null;
        }

        @Override
        public int getTimeOffset() {
            return 0;
        }

        @Override
        public InputStream getContentUnwrapped() {
            return getContent();
        }
    }
} 