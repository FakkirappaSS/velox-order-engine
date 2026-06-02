# ==========================================
# Stage 1: Build Phase
# ==========================================
FROM maven:3.8.5-openjdk-17-slim AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy sources and package
COPY src ./src
RUN mvn clean package -DskipTests

# ==========================================
# Stage 2: Runtime Phase
# ==========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy executable jar from build stage
COPY --from=build /app/target/velox-order-engine-*.jar app.jar

# Expose port
EXPOSE 8080

# Environment variables setup with demo profile default fallback
ENV SPRING_PROFILES_ACTIVE=demo

# Run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
