####
# This Dockerfile is used in order to build a container that runs the Quarkus application in JVM mode
#
# Before building the container image run:
#
# ./gradlew build
#
# Then, build the image with:
#
# docker build -f src/main/docker/Dockerfile.jvm -t quarkus/agent-engine-jvm .
#
# Then run the container using:
#
# docker run -i --rm -p 8080:8080 quarkus/agent-engine-jvm
#
# If you want to include the debug port into your docker image
# you will have to expose the debug port (default 5005) like this :  EXPOSE 8080 5005
#
# Then run the container using :
#
# docker run -i --rm -p 8080:8080 -p 5005:5005 -e JAVA_ENABLE_DEBUG="true" quarkus/agent-engine-jvm
#
###
FROM eclipse-temurin:25-jre-alpine

ENV LANGUAGE='en_US:en'

RUN addgroup -S quarkus && adduser -S quarkus -G quarkus

# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --chown=quarkus interfaces/rest/build/quarkus-app/lib/ /deployments/lib/
COPY --chown=quarkus interfaces/rest/build/quarkus-app/*.jar /deployments/
COPY --chown=quarkus interfaces/rest/build/quarkus-app/app/ /deployments/app/
COPY --chown=quarkus interfaces/rest/build/quarkus-app/quarkus/ /deployments/quarkus/
COPY --chown=quarkus plugins/ /deployments/plugins/

EXPOSE 18080
USER quarkus
ENV AB_JOLOKIA_OFF=""
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"
ENV PLUGIN_DIR="/deployments/plugins"

ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -jar $JAVA_APP_JAR" ]
