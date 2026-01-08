# core-banking-services/Dockerfile

# ============================================
# Build Stage
# ============================================
FROM gradle:8.14.3-jdk21-alpine AS build

WORKDIR /app

# Copy gradle files first for better caching
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# Download dependencies (cached if build.gradle hasn't changed)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build the application
RUN gradle build --no-daemon -x test && \
    gradle bootJar --no-daemon

# ============================================
# Development Target
# ============================================
FROM eclipse-temurin:21.0.9_10-jre-alpine AS development

LABEL maintainer="Purbank Dev Team <dev@purbank.ch>"
LABEL org.opencontainers.image.title="Purbank Core Banking Service (Development)"
LABEL org.opencontainers.image.description="Development build with debugging enabled"

# Install debugging tools
RUN apk add --no-cache \
    curl \
    wget \
    netcat-openbsd \
    postgresql-client \
    && rm -rf /var/cache/apk/*

# Create app user
RUN addgroup -S purbank && adduser -S purbank -G purbank

WORKDIR /app

# Copy built jar from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Set ownership
RUN chown -R purbank:purbank /app

USER purbank

# Expose application and debug ports
EXPOSE 8080 5005

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/v1/health || exit 1

# Development settings with remote debugging
ENV SPRING_PROFILES_ACTIVE=dev \
    JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

ENTRYPOINT ["java", "-jar", "app.jar"]

# ============================================
# Production Target
# ============================================
FROM eclipse-temurin:21.0.9_10-jre-alpine AS production

LABEL maintainer="Purbank Dev Team <dev@purbank.ch>"
LABEL org.opencontainers.image.title="Purbank Core Banking Service"
LABEL org.opencontainers.image.description="Production-ready core banking backend"

# Build arguments
ARG BUILD_DATE
ARG VERSION
ARG REVISION

# Labels for image metadata
LABEL org.opencontainers.image.created="${BUILD_DATE}"
LABEL org.opencontainers.image.version="${VERSION}"
LABEL org.opencontainers.image.revision="${REVISION}"

# Install minimal runtime dependencies
RUN apk add --no-cache \
    curl \
    && rm -rf /var/cache/apk/*

# Create app user
RUN addgroup -S purbank && adduser -S purbank -G purbank

WORKDIR /app

# Copy built jar from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Set ownership
RUN chown -R purbank:purbank /app

USER purbank

# Expose only application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/api/v1/health || exit 1

# Production JVM settings
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=50.0 \
               -XX:+UseG1GC \
               -XX:+OptimizeStringConcat \
               -XX:+UseStringDeduplication \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
