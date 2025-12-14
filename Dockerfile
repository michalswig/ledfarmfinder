# --- STAGE 1: build JAR ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests clean package


# --- STAGE 2: runtime (Playwright-ready) ---
# UWAGA: dopasuj wersję do tej, której używasz w projekcie (u Ciebie w logach było 1.56.0)
FROM mcr.microsoft.com/playwright/java:v1.56.0-jammy
WORKDIR /app

# JAR
COPY --from=build /app/target/*.jar app.jar

# W Playwright image przeglądarki zwykle są już w obrazie.
# Jeśli chcesz wymusić (bez pobierania w runtime) – zostaw:
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
RUN java -cp /app/app.jar com.microsoft.playwright.CLI install chromium

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
