FROM eclipse-temurin:23-jre
WORKDIR /app
COPY target/product-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]