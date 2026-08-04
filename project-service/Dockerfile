# Étape 1 : Utilisation d'une image JDK 21 pour l'exécution
# On remplace le 17 par 21
FROM eclipse-temurin:21-jdk-alpine

# Dossier de travail dans le container
WORKDIR /app

# Copier le fichier JAR généré par Maven (compilé en Java 21 via le pom.xml)
COPY target/*.jar app.jar

# Création du dossier pour stocker les PDF (très important pour ton ProjectIQ)
RUN mkdir -p uploads/projects

# Port exposé (le port 8083 que l'on a vu dans Eureka)
EXPOSE 8083

# Lancement de l'application
ENTRYPOINT ["java", "-jar", "app.jar"]