FROM eclipse-temurin:21-jre
RUN groupadd -r appuser && useradd -r -g appuser appuser
WORKDIR /app

COPY build/libs/*.jar app.jar

ENV SERVER_PORT=8080

EXPOSE 8080

USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
