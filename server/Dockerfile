# Multi-stage build for QMarket server
FROM eclipse-temurin@sha256:0bfc69a4758a86710e5c474032d28400a8bd00874766f9e8b1642ac2fd293159 AS build # 21.0.12.1_1-jdk-alpine
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :server:bootJar --no-daemon -x test

FROM eclipse-temurin@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555 # 21.0.12_8-jre-alpine
WORKDIR /app
RUN addgroup -S qmarket && adduser -S qmarket -G qmarket
COPY --from=build /workspace/server/build/libs/qmarket-server-*.jar app.jar
USER qmarket
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
