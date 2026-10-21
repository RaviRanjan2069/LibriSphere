# Builds and runs the Spring Boot server (used by Render).

# ---- Build the jar ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- Run it ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home app && mkdir -p /app/data/uploads && chown -R app /app
COPY --from=build /app/target/*.jar app.jar
USER app
# Fits the JVM into small free-tier containers (512 MB)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
EXPOSE 9090
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
