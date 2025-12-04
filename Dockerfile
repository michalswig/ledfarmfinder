# --- STAGE 1: build JAR ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# kopiujemy pliki Maven
COPY pom.xml .
COPY src ./src

# budujemy aplikację (bez testów)
RUN mvn -B -DskipTests clean package

# --- STAGE 2: lekki obraz do uruchomienia ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# kopiujemy zbudowany JAR z poprzedniego stage
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
