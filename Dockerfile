# Use a lightweight Java runtime base image
FROM openjdk:21-jdk-slim

# Add metadata about the maintainer (recommended way)
LABEL "org.opencontainers.image.authors"="eazybytes.com"

# Copy the built jar from the target directory into the Docker image
COPY target/securitytemplate-0.0.1-SNAPSHOT.jar securitytemplate-0.0.1-SNAPSHOT.jar

# Command to run the application
ENTRYPOINT ["java", "-jar", "securitytemplate-0.0.1-SNAPSHOT.jar"]
