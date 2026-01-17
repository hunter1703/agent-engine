FROM eclipse-temurin:21-jre

WORKDIR /app

COPY rest/build/quarkus-app/ /app/
COPY plugins/ /app/plugins/

ENV PLUGIN_DIR=/app/plugins
EXPOSE 8080

CMD ["java","-jar","/app/quarkus-run.jar"]
