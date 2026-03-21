FROM eclipse-temurin:21-jre
WORKDIR /app

COPY build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=dev \
    DATABASE_URL=jdbc:postgresql://postgres:5432/stopforfuel \
    DATABASE_USER=postgres \
    DATABASE_PASSWORD=password \
    SERVER_PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
