# Build stage: compile the Spring Boot jar
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Run stage: only the JRE and the jar, running as a non-root user
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 docdrift
COPY --from=build /app/target/*.jar app.jar
USER docdrift
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
