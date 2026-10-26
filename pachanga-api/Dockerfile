# =====================================================================
# Receta para construir y arrancar la API en un contenedor (la usa Render).
# Tiene dos fases para que la imagen final sea pequeña:
#   1. "compilar": una imagen con Maven y el JDK compila el proyecto y genera el .jar.
#   2. "final": una imagen solo con Java (JRE) copia ese .jar y lo ejecuta.
# =====================================================================

# ---------- Fase 1: compilar ----------
FROM maven:3.9-eclipse-temurin-21 AS compilar
WORKDIR /app

# Primero solo el pom.xml: así Docker guarda en caché las dependencias descargadas
# y no las vuelve a bajar si solo cambia el código.
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
# -DskipTests: los tests necesitan la base de datos de Docker de tu PC; se pasan antes de subir.
RUN mvn -q package -DskipTests

# ---------- Fase 2: imagen final ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=compilar /app/target/*.jar app.jar

# El plan gratuito de Render da 512 MB de memoria. Java usa como mucho el 75 %
# y el recolector de basura "Serial", que es el que menos memoria gasta.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

# Render pasa el puerto en la variable PORT (ver server.port en application.properties).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
