FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

# Copie du JAR (généré par ./gradlew bootJar)
COPY target/*.jar app.jar

# Port par défaut d'Eureka
EXPOSE 8761

# On force le port au cas où
ENV SERVER_PORT=8761

ENTRYPOINT ["java", "-jar", "app.jar"]