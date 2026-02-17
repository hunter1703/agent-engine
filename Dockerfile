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
FROM registry.access.redhat.com/ubi8/openjdk-17:1.15

ENV LANGUAGE='en_US:en'


# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --chown=185 interfaces/rest/build/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 interfaces/rest/build/quarkus-app/*.jar /deployments/
COPY --chown=185 interfaces/rest/build/quarkus-app/app/ /deployments/app/
COPY --chown=185 interfaces/rest/build/quarkus-app/quarkus/ /deployments/quarkus/
COPY --chown=185 plugins/ /deployments/plugins/

EXPOSE 8080
USER 185
ENV AB_JOLOKIA_OFF=""
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"
ENV PLUGIN_DIR="/deployments/plugins"
