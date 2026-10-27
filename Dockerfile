FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
COPY target/ai-defense-layer-*.jar /app/app.jar
EXPOSE 8080 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
