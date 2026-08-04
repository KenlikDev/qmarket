# Multi-stage build for QMarket server
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :server:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S qmarket && adduser -S qmarket -G qmarket
COPY --from=build /workspace/server/build/libs/*.jar app.jar
USER qmarket
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
