# ---------- Etapa 1: build ----------
# Compila el proyecto con Maven + JDK 21 dentro del contenedor.
# (JDK 21 es obligatorio: Lombok no funciona con JDK 25.)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Primero solo el pom para cachear la descarga de dependencias:
# si el codigo cambia pero el pom no, Docker reutiliza esta capa.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Ahora el codigo fuente y empaquetado (sin tests).
COPY src ./src
RUN mvn -B -DskipTests clean package

# ---------- Etapa 2: runtime ----------
# Imagen ligera: solo JRE 21 + el jar resultante.
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Copia el jar generado en la etapa de build.
COPY --from=build /app/target/*.jar app.jar

# Puerto de matchmaking (convencion: gateway 8080, auth 8081, matchmaking 8082).
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
