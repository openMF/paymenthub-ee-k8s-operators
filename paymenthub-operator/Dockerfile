# Use an official openjdk runtime as a parent image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY build/libs/paymenthub-operator-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
