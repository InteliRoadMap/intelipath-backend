# Stage 1: Build the application using Maven
FROM maven:3.9-eclipse-temurin-21 AS builder

# Set the working directory
WORKDIR /app

# Copy the pom.xml and download dependencies
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Copy the source code
COPY src ./src

# Package the application (skip compiling/running tests for faster Docker builds)
RUN mvn -B -q clean package -Dmaven.test.skip=true

# Stage 2: Run the application using a lightweight JRE image
FROM eclipse-temurin:21-jre-alpine

# Set the working directory
WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Run as a non-root, unprivileged user: a container escape or RCE in the app
# then can't touch anything outside its own file ownership.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

# Expose the application port
EXPOSE 8080

# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
