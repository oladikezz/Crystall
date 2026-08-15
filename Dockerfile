# Stage 1: Build
FROM gradle:8.5-jdk21 AS builder
WORKDIR /app
COPY . /app/
WORKDIR /app/core
RUN gradle installDist --no-daemon

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/core/build/install/core /app/
COPY start.sh /app/
RUN chmod +x /app/start.sh
EXPOSE 25565
ENTRYPOINT ["/app/start.sh"]
