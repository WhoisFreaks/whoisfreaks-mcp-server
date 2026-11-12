# syntax=docker/dockerfile:1
#
# Multi-platform image — verified on:
#   macOS   : Apple Silicon (arm64) and Intel (amd64) via Docker Desktop
#   Windows : Docker Desktop with WSL2 backend (amd64)
#   Ubuntu  : x86_64 (amd64) and ARM64 (arm64) servers
#
# Build for local platform:
#   docker compose -f deploy/docker-compose.yml up -d --build
#
# Build & push multi-arch image to Docker Hub:
#   docker buildx build --platform linux/amd64,linux/arm64 \
#     -f deploy/Dockerfile -t whoisfreaks/mcp-server:<version> --push .

# ---- Build stage ----
# --platform=$BUILDPLATFORM ensures Maven always compiles on the native host
# architecture, avoiding slow QEMU emulation during the build step.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies first (cached layer — speeds up rebuilds)
RUN mvn dependency:go-offline -q
COPY src/ src/
RUN mvn clean package -q -DskipTests && \
    cp target/whoisfreaks-mcp-server-*.jar target/app.jar

# ---- Runtime stage ----
# eclipse-temurin:17-jre-jammy (Ubuntu 22.04 LTS) ships both linux/amd64 and
# linux/arm64 manifest entries. Docker automatically pulls the correct variant
# for the target platform — no manual platform flag required.
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Create a non-root system user for security
RUN groupadd --system whoisfreaks && \
    useradd --system --no-create-home --shell /usr/sbin/nologin --gid whoisfreaks whoisfreaks

# Copy the fat JAR produced by the build stage
COPY --from=build /app/target/app.jar app.jar
RUN chown whoisfreaks:whoisfreaks app.jar

USER whoisfreaks

# Only used in stdio mode (docker run -i --entrypoint java ... -jar app.jar).
# The default HTTP mode needs no server-side key: each client sends its own.
ENV WHOISFREAKS_API_KEY=""
ENV PORT=3100

# Hosted multi-user Streamable HTTP server at http://<host>:3100/mcp
EXPOSE 3100

CMD ["java", "-jar", "app.jar", "--http"]
