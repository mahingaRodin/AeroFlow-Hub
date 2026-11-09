FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl \
    && addgroup -S aeroflow \
    && adduser -S -G aeroflow aeroflow
WORKDIR /app
COPY --from=build --chown=aeroflow:aeroflow /workspace/target/aeroflow-hub-0.0.1-SNAPSHOT.jar app.jar
USER aeroflow
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
