# --- STAGE 1: build JAR ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests clean package

# --- STAGE 2: runtime ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# 1) Systemowe zależności potrzebne Chromium (Playwright)
RUN apt-get update && apt-get install -y \
  ca-certificates \
  libnss3 libnspr4 \
  libatk1.0-0 libatk-bridge2.0-0 \
  libcups2 libdrm2 libxkbcommon0 \
  libxcomposite1 libxdamage1 libxfixes3 libxrandr2 \
  libgbm1 libgtk-3-0 \
  libasound2 \
  libx11-6 libx11-xcb1 libxcb1 \
  libxext6 libxrender1 \
  fonts-liberation \
  && rm -rf /var/lib/apt/lists/*

# 2) JAR
COPY --from=build /app/target/*.jar app.jar

# 3) Ścieżka na przeglądarki Playwright (żeby były w obrazie)
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
RUN mkdir -p /ms-playwright

# 4) Instalacja Chromium do Playwright w trakcie builda obrazu
#    (ważne: dzięki temu na Renderze w runtime nie będzie pobierania ani błędu "browser not found")
RUN java -cp /app/app.jar com.microsoft.playwright.CLI install chromium

EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
