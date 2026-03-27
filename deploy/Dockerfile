# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies first (cached layer)
RUN mvn dependency:go-offline -q
COPY src/ src/
RUN mvn clean package -q -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create a non-root user
RUN addgroup -S whoisfreaks && adduser -S whoisfreaks -G whoisfreaks

COPY --from=build /app/target/whoisfreaks-mcp-server-1.0.0.jar app.jar
RUN chown whoisfreaks:whoisfreaks app.jar

USER whoisfreaks

# Environment variables (override at runtime)
ENV WHOISFREAKS_API_KEY=""
ENV WHOISFREAKS_IP_WHOIS_URL="http://ip-whois-api:8080"
ENV WHOISFREAKS_ASN_WHOIS_URL="http://asn-whois-api:8081"

ENTRYPOINT ["java", "-jar", "app.jar"]
