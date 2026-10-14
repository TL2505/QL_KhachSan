# ==============================================================================
# Multi-stage Hardened Alpine Production Container for 5★ Luxury Hotel PMS
# Base OS: Alpine Linux 3.19 | Runtime: Eclipse Temurin JRE 17 (Pure Java)
# Constraints: 0 Critical / 0 High CVEs, Non-root execution, G1GC memory tuning
# Reference: TASK-D4.1, TASK-D4.3 (DevOps & Production Hardening)
# ==============================================================================

# ------------------------------------------------------------------------------
# STAGE 1: Dependency Resolution & Application Build
# ------------------------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# 1. Cache Maven POM and dependencies
COPY pom.xml ./
COPY owasp-suppressions.xml ./

# 2. Copy source code and local libraries
COPY src/ ./src/
COPY lib/ ./lib/

# 3. Package application and copy runtime dependencies (skip unit tests for fast reproducible packaging)
RUN mvn clean package -DskipTests

# ------------------------------------------------------------------------------
# STAGE 2: Minimal Hardened Production Runtime (Alpine Linux JRE 17)
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 1. Timezone configuration for Vietnam (UTC+7) & install curl for healthchecks
ENV TZ=Asia/Ho_Chi_Minh
RUN apk add --no-cache curl tzdata && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

# 2. Security Hardening: Create dedicated unprivileged system user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 3. Copy packaged application jar, dependency libraries, and classes from builder
COPY --from=builder /build/target/QL_KhachSan-1.0-SNAPSHOT.jar ./app.jar
COPY --from=builder /build/target/dependency ./lib
COPY --from=builder /build/lib ./lib-extra
COPY --from=builder /build/target/classes ./classes

# 4. Set strict file ownership to unprivileged user
RUN chown -R appuser:appgroup /app

# 5. Production JVM container tuning & random entropy optimization
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# 6. Expose Core Backend HTTP Server Port (com.sun.net.httpserver)
EXPOSE 8081

# 7. Native Docker Healthcheck against deep health check endpoint
HEALTHCHECK --interval=5s --timeout=3s --retries=5 --start-period=10s \
  CMD curl -f http://localhost:8081/api/v1/health || exit 1

# 8. Defense-in-Depth: Run as unprivileged non-root user
USER appuser

# 9. Launch PMS Server
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp 'app.jar:lib/*:lib-extra/*:classes' quanlykhachsan.backend.Main"]
