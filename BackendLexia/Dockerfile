# =============================================================================
# LEXIA — Imagen del backend para Render (o cualquier host con Docker)
# =============================================================================
# Etapa 1: compila el jar con Maven y JDK 21 (con JDK >= 24 Lombok no corre).
# Etapa 2: solo el JRE 21 y el jar, para que la imagen pese poco.
#
# Variables requeridas en el host (perfil prod, ver application-prod.yml):
#   DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRETO, CORS_ORIGENES
# Solo en el primer arranque: LEXIA_ESTUDIO_RUC, LEXIA_ADMIN_EMAIL, LEXIA_ADMIN_PASSWORD
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package && cp target/*.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 lexia && mkdir -p /app/storage && chown -R lexia /app
COPY --from=build /app/app.jar app.jar
USER lexia

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"

EXPOSE 8080
# Render inyecta PORT; en local se usa 8080.
CMD ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
