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
#     -f deploy/Dockerfile -t whoisfreaks/mcp-server:1.0.0 --push .

# ---- Build stage ----
# --platform=$BUILDPLATFORM ensures Maven always compiles on the native host
# architecture, avoiding slow QEMU emulation during the build step.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies first (cached layer — speeds up rebuilds)
RUN mvn dependency:go-offline -q
COPY src/ src/
RUN mvn clean package -q -DskipTests

# ---- Runtime stage ----
# eclipse-temurin:17-jre-jammy (Ubuntu 22.04 LTS) ships both linux/amd64 and
# linux/arm64 manifest entries. Docker automatically pulls the correct variant
# for the target platform — no manual platform flag required.
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Install Node.js LTS via NodeSource (ships a modern npm — avoids outdated Ubuntu package)
# then install mcp-proxy: the official stdio→HTTP/SSE bridge for MCP servers.
# Package: https://www.npmjs.com/package/mcp-proxy
RUN apt-get update -qq && \
    apt-get install -y --no-install-recommends curl ca-certificates && \
    curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - && \
    apt-get install -y --no-install-recommends nodejs && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    npm install -g mcp-proxy

# Create a non-root system user for security
RUN groupadd --system whoisfreaks && \
    useradd --system --no-create-home --shell /usr/sbin/nologin --gid whoisfreaks whoisfreaks

# Copy the fat JAR produced by the build stage
COPY --from=build /app/target/whoisfreaks-mcp-server-1.0.0.jar app.jar
RUN chown whoisfreaks:whoisfreaks app.jar

USER whoisfreaks

# Injected at runtime via -e flag, docker-compose environment, or .env file
ENV WHOISFREAKS_API_KEY=""

# mcp-proxy listens on 3100 and spawns the Java MCP server as a stdio subprocess
EXPOSE 3100

CMD ["mcp-proxy", "--port", "3100", "--", "java", "-jar", "app.jar"]
