FROM eclipse-temurin:21-jre AS base

WORKDIR /app

COPY target/omno-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]