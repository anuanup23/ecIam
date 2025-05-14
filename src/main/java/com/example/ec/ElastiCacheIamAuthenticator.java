package com.example.ec;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.http.HttpMethodName;
import redis.clients.jedis.JedisClientConfig;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
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
            // Get the credentials
            AWSCredentials credentials = credentialsProvider.getCredentials();
            logger.debug("Using credentials: {}", credentials.getAWSAccessKeyId());
            
            // For ElastiCache IAM auth with Jedis, we use a literal token
            // with the following format (directly from aws redis auth docs)
            // Note: This doesn't use AWS SigV4 standard like the previous attempts
            String token = String.format(
                "%s:%s",
                username,
                credentials.getAWSAccessKeyId()
            );
            
            // Add session token if available
            if (credentials instanceof AWSSessionCredentials) {
                token = String.format(
                    "%s:%s:%s",
                    username,
                    credentials.getAWSAccessKeyId(),
                    ((AWSSessionCredentials) credentials).getSessionToken()
                );
            }
            
            logger.debug("Generated auth token for user {}", username);
            return token;
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
} 