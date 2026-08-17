# Utilisation d'une image stable et maintenue pour Java 21
FROM eclipse-temurin:21-jdk-alpine

# Répertoire de travail
WORKDIR /app

# Copie du jar généré (assure-toi que le nom correspond)
COPY target/*.jar app.jar

# Le rapport d'audit doit conserver la mise en page Word officielle même dans
# une image Docker. Une copie explicite évite toute dépendance au classpath.
RUN mkdir -p /app/templates
COPY src/main/resources/templates/Template_Rapport_Audit_ProjectIQ.docx /app/templates/Template_Rapport_Audit_ProjectIQ.docx

# Port d'écoute
EXPOSE 8086

# Lancement de l'application
ENTRYPOINT ["java", "-jar", "app.jar"]
