# Stage 1: build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY pom.xml mvnw ./
COPY .mvn .mvn/
RUN chmod +x mvnw

COPY src src/
RUN ./mvnw clean package -DskipTests

# Stage 2: run
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/fhir-server-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]
