# Utilisation d'une image stable et maintenue pour Java 21
FROM eclipse-temurin:21-jdk-alpine

# Répertoire de travail
WORKDIR /app

# Copie du jar généré (assure-toi que le nom correspond)
COPY target/*.jar app.jar

# Port d'écoute
EXPOSE 8086

# Lancement de l'application
ENTRYPOINT ["java", "-jar", "app.jar"]