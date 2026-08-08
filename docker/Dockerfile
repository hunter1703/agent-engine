FROM eclipse-temurin:25-jre-alpine

ARG SERVICE_MODULE

# create non-root user
RUN addgroup -S quarkus && adduser -S quarkus -G quarkus

# copy jars and make the non-root user owner
COPY --chown=quarkus ${SERVICE_MODULE}/build/quarkus-app/lib/ /deployments/lib/
COPY --chown=quarkus ${SERVICE_MODULE}/build/quarkus-app/*.jar /deployments/
COPY --chown=quarkus ${SERVICE_MODULE}/build/quarkus-app/app/ /deployments/app/
COPY --chown=quarkus ${SERVICE_MODULE}/build/quarkus-app/quarkus/ /deployments/quarkus/

# change current user to the created non-root user
USER quarkus

ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS $JAVA_ARGS -jar /deployments/quarkus-run.jar" ]
