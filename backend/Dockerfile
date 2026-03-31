FROM eclipse-temurin:21-jre
RUN groupadd -r appuser && useradd -r -g appuser appuser
WORKDIR /app

COPY build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-prod} \
    DATABASE_URL=${DATABASE_URL} \
    DATABASE_USER=${DATABASE_USER} \
    DATABASE_PASSWORD=${DATABASE_PASSWORD} \
    SERVER_PORT=8080

EXPOSE 8080

USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
