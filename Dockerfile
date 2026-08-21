# Stage 1: Build
FROM gradle:9.0-jdk-current AS builder
WORKDIR /app
COPY . /app/
RUN ./gradlew jar --no-daemon

# Stage 2: Run
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /app/core/build/libs/crystall-core-1.0.0.jar /app/crystall-core.jar
COPY start.sh /app/
RUN chmod +x /app/start.sh
EXPOSE 25565 25566 8080
ENTRYPOINT ["/app/start.sh"]
