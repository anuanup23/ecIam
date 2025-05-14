package com.example.ec;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example for connecting to ElastiCache Serverless with IAM authentication
 * and setting a key-value pair.
 */
public class ECServerlessExample {
    private static final Logger logger = LoggerFactory.getLogger(ECServerlessExample.class);

    // ElastiCache Serverless configuration
    private static final String CACHE_NAME = "cache-01-vk";
    private static final String REGION = "eu-west-1";
    private static final String USERNAME = "iam-user-01";
    
    // Specific endpoint for the cache
    private static final String DEFAULT_ENDPOINT = "cache-01-vk-yiy6se.serverless.euw1.cache.amazonaws.com";
    
    // These can be overridden by command-line arguments or environment variables
    private static String elastiCacheEndpoint;
    private static int elastiCachePort = 6379;

    public static void main(String[] args) {
        // Parse command-line arguments
        parseArgs(args);
        
        logger.info("Connecting to ElastiCache Serverless at {}:{}", elastiCacheEndpoint, elastiCachePort);
        logger.info("Using IAM user: {}", USERNAME);
        logger.info("Using region: {}", REGION);
        logger.info("NOTE: ElastiCache can only be accessed from within AWS VPC or through a VPC peering connection");
        
        try {
            // Check AWS credentials
            logger.info("Checking AWS credentials...");
            try {
                com.amazonaws.auth.DefaultAWSCredentialsProviderChain credentialsProvider = 
                    com.amazonaws.auth.DefaultAWSCredentialsProviderChain.getInstance();
                com.amazonaws.auth.AWSCredentials credentials = credentialsProvider.getCredentials();
                logger.info("AWS credentials found for access key ID: {}", 
                    credentials.getAWSAccessKeyId().substring(0, 5) + "...");
            } catch (Exception e) {
                logger.error("Error retrieving AWS credentials", e);
            }
            
            // Create IAM authenticator for ElastiCache
            logger.info("Creating IAM authenticator...");
            ElastiCacheIamAuthenticator authenticator = new ElastiCacheIamAuthenticator(
                    USERNAME, REGION, elastiCacheEndpoint, elastiCachePort);
            
            // Connect to ElastiCache Serverless
            logger.info("Attempting connection with SSL enabled: {}", authenticator.isSsl());
            try (Jedis jedis = new Jedis(elastiCacheEndpoint, elastiCachePort, authenticator)) {
                // Set key-value pair "test" -> "test"
                logger.info("Setting key 'test' to value 'test'...");
                String result = jedis.set("test", "test");
                logger.info("Set key 'test' with value 'test'. Result: {}", result);
                
                // Verify the value was set correctly
                logger.info("Retrieving key 'test'...");
                String value = jedis.get("test");
                logger.info("Retrieved value for key 'test': {}", value);
            }
            
            logger.info("Example completed successfully");
        } catch (Exception e) {
            logger.error("Error connecting to ElastiCache or performing operations", e);
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void parseArgs(String[] args) {
        if (args.length < 1) {
            // Check environment variables if args not provided
            elastiCacheEndpoint = System.getenv("ELASTICACHE_ENDPOINT");
            
            String portString = System.getenv("ELASTICACHE_PORT");
            if (portString != null && !portString.isEmpty()) {
                try {
                    elastiCachePort = Integer.parseInt(portString);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid port format in environment variable. Using default port 6379.");
                }
            }
            
            if (elastiCacheEndpoint == null || elastiCacheEndpoint.isEmpty()) {
                // Use the specific endpoint provided
                elastiCacheEndpoint = DEFAULT_ENDPOINT;
                logger.info("Using default ElastiCache endpoint: {}", elastiCacheEndpoint);
            }
        } else {
            elastiCacheEndpoint = args[0];
            
            if (args.length >= 2) {
                try {
                    elastiCachePort = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid port format. Using default port 6379.");
                }
            }
        }
    }
    
    private static void printUsage() {
        logger.info("Usage: java -jar ec-serverless-iam-example.jar <elasticache-endpoint> [port]");
        logger.info("  or set environment variables ELASTICACHE_ENDPOINT and optionally ELASTICACHE_PORT");
        logger.info("  Default endpoint: " + DEFAULT_ENDPOINT);
    }
} 