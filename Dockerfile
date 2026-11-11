FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd -r mock && useradd -r -g mock mock
COPY --from=build /workspace/target/header-driven-wiremock-service-1.0.0-SNAPSHOT.jar /app/app.jar
COPY --chown=mock:mock mock-data /app/mock-data
USER mock
EXPOSE 8080 9090
ENV MOCK_DATA_DIR=/app/mock-data ADMIN_BIND_ADDRESS=0.0.0.0
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
