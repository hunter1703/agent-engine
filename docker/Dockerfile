####
# Multi-service Dockerfile for Agent Engine
# Build with: docker build --build-arg SERVICE_MODULE=<module> -f docker/Dockerfile .
#
# SERVICE_MODULE options:
#   - core
#   - runtime
#   - interfaces/rest
####
FROM eclipse-temurin:26-jre-alpine

ARG SERVICE_MODULE=interfaces/rest

ENV LANGUAGE='en_US:en'

RUN addgroup -S quarkus && adduser -S quarkus -G quarkus

COPY --chown=quarkus ${SERVICE_MODULE}/build/quarkus-app/lib/ /deployments/lib/
COPY --chown=quarkus ${SERVICE_MODULE}/build/quarkus-app/*.jar /deployments/
COPY --chown=quarkus ${SERVICE_MODULE}/build/quarkus-app/app/ /deployments/app/
COPY --chown=quarkus ${SERVICE_MODULE}/build/quarkus-app/quarkus/ /deployments/quarkus/
COPY --chown=quarkus configs/ /deployments/configs/

RUN mkdir -p /deployments/plugins

ENV QUARKUS_HTTP_PORT=${QUARKUS_HTTP_PORT:-8080}
ENV QUARKUS_GRPC_SERVER_PORT=${QUARKUS_GRPC_SERVER_PORT:-9000}

EXPOSE 8080 9000 2552

USER quarkus
ENV AB_JOLOKIA_OFF=""
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"
ENV PLUGIN_DIR="/deployments/plugins"

ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS $JAVA_ARGS -jar $JAVA_APP_JAR" ]
