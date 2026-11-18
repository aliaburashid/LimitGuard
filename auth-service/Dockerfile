# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS build


WORKDIR /app

# Download Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy Java source code
COPY src ./src

# Build the Spring Boot JAR
RUN mvn clean package -DskipTests -B

# Compile a dependency-free HTTP readiness probe using the build-stage JDK.
COPY docker/HttpHealthCheck.java /tmp/HttpHealthCheck.java
RUN javac --release 17 -d /app/health /tmp/HttpHealthCheck.java

# Stage 2: Run the application
FROM eclipse-temurin:17-jre-noble


WORKDIR /app

# Copy the JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

COPY --from=build /app/health /app/health

HEALTHCHECK --interval=5s --timeout=5s --start-period=30s --retries=12 \
    CMD ["java", "-cp", "/app/health", "HttpHealthCheck"]

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
