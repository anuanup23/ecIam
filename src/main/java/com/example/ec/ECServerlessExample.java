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
    private static final String USERNAME = "iam-user";
    
    // Specific endpoint for the cache
    private static final String DEFAULT_ENDPOINT = "cache-01-vk-yiy6se.serverless.euw1.cache.amazonaws.com";
    
    // These can be overridden by command-line arguments or environment variables
    private static String elastiCacheEndpoint;
    private static int elastiCachePort = 6379;

    public static void main(String[] args) {
        // Parse command-line arguments
        parseArgs(args);
        
        logger.info("Connecting to ElastiCache Serverless at {}:{}", elastiCacheEndpoint, elastiCachePort);
        
        try {
            // Create IAM authenticator for ElastiCache
            ElastiCacheIamAuthenticator authenticator = new ElastiCacheIamAuthenticator(
                    USERNAME, REGION, elastiCacheEndpoint, elastiCachePort);
            
            // Connect to ElastiCache Serverless
            try (Jedis jedis = new Jedis(elastiCacheEndpoint, elastiCachePort, authenticator)) {
                // Set key-value pair "test" -> "test"
                String result = jedis.set("test", "test");
                logger.info("Set key 'test' with value 'test'. Result: {}", result);
                
                // Verify the value was set correctly
                String value = jedis.get("test");
                logger.info("Retrieved value for key 'test': {}", value);
            }
            
            logger.info("Example completed successfully");
        } catch (Exception e) {
            logger.error("Error connecting to ElastiCache or performing operations", e);
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