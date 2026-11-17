# =============================================================================
# LEXIA — Imagen del backend para Render (o cualquier host con Docker)
# =============================================================================
# Etapa 1: compila el jar con Maven y JDK 21 (con JDK >= 24 Lombok no corre).
# Etapa 2: solo el JRE 21, el jar descomprimido y un archivo CDS.
#
# Variables requeridas en el host (perfil prod, ver application-prod.yml):
#   DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRETO, CORS_ORIGENES
# Solo en el primer arranque: LEXIA_ESTUDIO_RUC, LEXIA_ADMIN_EMAIL, LEXIA_ADMIN_PASSWORD
#
# ARRANQUE RAPIDO. El plan gratis de Render apaga el servicio tras 15 min sin
# uso y lo vuelve a arrancar con la siguiente visita, con muy poca CPU: sin
# estos ajustes tardaba ~160 s. Medido en local con 1 nucleo: 9,1 s -> 3,9 s.
#  - CDS (Class Data Sharing): las clases quedan precargadas en app.jsa,
#    generado aqui con un arranque de entrenamiento. Si algun dia no
#    coincide con el jar, la JVM lo ignora y arranca normal.
#  - TieredStopAtLevel=1: solo el compilador C1, menos trabajo al iniciar.
#  - lazy-initialization: los beans se crean al primer uso. Flyway, JPA y las
#    tareas @Scheduled (marcadas @Lazy(false)) siguen iniciandose al arrancar.
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package && cp target/*.jar app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 lexia
WORKDIR /app
COPY --from=build /app/app.jar /tmp/app.jar
RUN java -Djarmode=tools -jar /tmp/app.jar extract --destination /app && rm /tmp/app.jar

# Arranque de entrenamiento para CDS: levanta el contexto y sale (onRefresh).
# No hay base en la construccion de la imagen, asi que se apagan Flyway y la
# lectura de metadatos JDBC; los valores de abajo son de mentira y no salen
# de este paso. Mismos GC y compilador que en ejecucion, o el archivo no sirve.
RUN java -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:ArchiveClassesAtExit=app.jsa \
      -Dspring.context.exit=onRefresh -Dserver.port=0 -Dlogging.level.root=WARN \
      -Dspring.profiles.active=prod -Dspring.flyway.enabled=false \
      -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -Dspring.jpa.database-platform=org.hibernate.dialect.MySQLDialect \
      -DDB_URL=jdbc:mysql://127.0.0.1:1/entrenamiento -DDB_USERNAME=x -DDB_PASSWORD=x \
      -DJWT_SECRETO=entrenamiento-cds-no-es-un-secreto-real-32b \
      -DCORS_ORIGENES=http://localhost \
      -jar app.jar \
 && mkdir -p /app/storage && chown -R lexia /app/storage

USER lexia
ENV SPRING_PROFILES_ACTIVE=prod \
    SPRING_MAIN_LAZY_INITIALIZATION=true \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"

EXPOSE 8080
# Render inyecta PORT; en local se usa 8080.
CMD ["sh", "-c", "exec java $JAVA_OPTS -XX:SharedArchiveFile=app.jsa -Dserver.port=${PORT:-8080} -jar app.jar"]
