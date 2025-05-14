# ElastiCache Serverless IAM Authentication Example

This Maven project demonstrates how to connect to Amazon ElastiCache Serverless using IAM authentication and perform a basic key-value operation.

## Prerequisites

1. Java 8 or higher
2. Maven
3. AWS credentials configured (via environment variables, AWS profile, EC2 instance profile, etc.)
4. An ElastiCache Serverless cluster with IAM authentication enabled
5. IAM policy that allows connecting to ElastiCache
6. **Network connectivity to ElastiCache** (critical - see Deployment section below)

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

## Deployment

### Important: Network Connectivity Requirements

ElastiCache instances are **not directly accessible from the public internet**. You must deploy this application in an environment that has network connectivity to your ElastiCache cluster.

**Valid deployment options:**

1. **EC2 instance in the same VPC** as your ElastiCache cluster
2. EC2 instance in a VPC with VPC peering to the ElastiCache VPC
3. On-premises server connected via AWS Direct Connect or VPN
4. AWS Lambda function in the same VPC as ElastiCache

**Testing locally:** If you want to test from your local machine, you would need to:
1. Set up an AWS VPN connection
2. Configure SSH tunneling through a bastion host in the VPC
3. Use AWS Cloud9 IDE which runs in the AWS environment

## Running the Example

Once deployed to an environment with proper network connectivity:

```bash
java -jar target/ec-serverless-iam-example-1.0-SNAPSHOT-jar-with-dependencies.jar
```

Alternatively, you can specify a custom endpoint and port:

```bash
java -jar target/ec-serverless-iam-example-1.0-SNAPSHOT-jar-with-dependencies.jar <your-elasticache-endpoint> [port]
```

Or use environment variables:

```bash
export ELASTICACHE_ENDPOINT=your-elasticache-endpoint
export ELASTICACHE_PORT=6379

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

1. **Connection timeouts**: This typically means you don't have network connectivity to ElastiCache. Make sure you're running the application from an environment that has access to the ElastiCache VPC.

2. **Authentication errors**: Check the IAM permissions and ensure the user has the proper policy attached. Verify the username matches what's configured in ElastiCache.

3. **DNS resolution issues**: Make sure you're using the correct endpoint for your ElastiCache instance.

4. **SSL/TLS errors**: ElastiCache requires SSL. The client is configured to use SSL by default. 