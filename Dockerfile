# --- STAGE 1: build JAR ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests clean package

# --- STAGE 2: runtime (Playwright-ready + browsers inside) ---
FROM mcr.microsoft.com/playwright/java:v1.56.0-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
