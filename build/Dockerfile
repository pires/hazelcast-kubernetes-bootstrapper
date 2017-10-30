FROM openjdk:8-jre-alpine

ADD app.jar app.jar

ENTRYPOINT java $JAVA_OPTIONS -Djava.security.egd=file:/dev/./urandom -jar /app.jar
