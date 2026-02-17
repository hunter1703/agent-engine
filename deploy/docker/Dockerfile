FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY settings.gradle build.gradle gradle.properties ./
COPY buildSrc buildSrc
COPY engine engine
COPY interfaces interfaces
COPY plugins plugins

RUN ./gradlew --no-daemon preparePluginsOptimized :interfaces:rest:quarkusBuild

FROM eclipse-temurin:21-jre

WORKDIR /opt/agent

ENV PLUGIN_DIR=/opt/agent/plugins
ENV QUARKUS_HTTP_HOST=0.0.0.0

COPY --from=build /workspace/interfaces/rest/build/quarkus-app/ /opt/agent/quarkus-app/
COPY --from=build /workspace/plugins/*.jar /opt/agent/plugins/

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/opt/agent/quarkus-app/quarkus-run.jar"]
