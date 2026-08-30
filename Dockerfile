# syntax=docker/dockerfile:1

# ---- Build stage --------------------------------------------------------
FROM maven:3.9.15-eclipse-temurin-26 AS build
WORKDIR /build

# Cache dependencies separately from source for faster rebuilds.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# ---- Runtime stage --------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime

# No root user, no build tools, no Maven cache, no secrets in the final image.
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /build/target/ai-shift-planner.jar app.jar

USER app
EXPOSE 8080

# Respects SIGTERM promptly and lets Spring Boot's graceful-shutdown window finish
# in-flight requests before the process exits (see server.shutdown=graceful).
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
