# ElastiCache Serverless IAM Authentication Example

This Maven project demonstrates how to connect to Amazon ElastiCache Serverless using IAM authentication and perform a basic key-value operation.

## Prerequisites

1. Java 8 or higher
2. Maven
3. AWS credentials configured (via environment variables, AWS profile, EC2 instance profile, etc.)
4. An ElastiCache Serverless cluster with IAM authentication enabled
5. IAM policy that allows connecting to ElastiCache
6. Network connectivity to ElastiCache (typically by running from an EC2 instance in the same VPC or through VPN)

## Configuration

This example is configured for:
- Cache name: cache-01-vk
- Region: eu-west-1
- Default endpoint: cache-01-vk-yiy6se.serverless.euw1.cache.amazonaws.com
- Port: 6379
- IAM Username: iam-user-01

If your cache has a different endpoint, you can provide it as a command-line argument or environment variable.

## Building the Project

```bash
mvn clean package
```

This will create a JAR file in the `target` directory named `ec-serverless-iam-example-1.0-SNAPSHOT-jar-with-dependencies.jar`.

## Running the Example

You can run the example by providing the ElastiCache endpoint as a command-line argument:

```bash
java -jar target/ec-serverless-iam-example-1.0-SNAPSHOT-jar-with-dependencies.jar <your-elasticache-endpoint> [port]
```

Alternatively, you can set environment variables:

```bash
export ELASTICACHE_ENDPOINT=your-elasticache-endpoint
export ELASTICACHE_PORT=6379  # Optional, defaults to 6379

java -jar target/ec-serverless-iam-example-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## What the Example Does

This example:

1. Connects to ElastiCache Serverless using IAM authentication
2. Sets a key-value pair with key "test" and value "test"
3. Retrieves the value to verify it was set correctly

## AWS IAM Policy Example

You'll need an IAM policy that allows connecting to your ElastiCache Serverless cluster. Here's an example policy:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "elasticache:Connect"
            ],
            "Resource": [
                "arn:aws:elasticache:eu-west-1:account-id:serverlessv2:cache-01-vk"
            ]
        }
    ]
}
```

Replace `account-id` with your AWS account ID.

## Troubleshooting

If you encounter issues:

1. Ensure your AWS credentials are properly configured and have the necessary permissions
2. Verify the ElastiCache endpoint is correct
3. Check that your ElastiCache Serverless cluster has IAM authentication enabled
4. Ensure your security groups allow connections to the ElastiCache cluster
5. Make sure you're running this from an environment that has network connectivity to ElastiCache (EC2 instance in the same VPC, VPN connection, etc.) 