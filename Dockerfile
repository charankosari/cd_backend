# Built by Railway on its servers; no Docker needed on your machine.

# ---- Stage 1: compile the jar with Maven + JDK 21
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
# Download dependencies first (cached between builds as long as pom.xml doesn't change)
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Tests already run locally / in CI; skip them here to keep deploys fast
RUN mvn -q -B -DskipTests package

# ---- Stage 2: small runtime image with only the JRE + the jar
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/feedback-backend-0.1.0.jar app.jar
# Use up to 75% of the container's memory for the Java heap
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
# Railway injects PORT; application.yml reads ${PORT:8081}
EXPOSE 8081
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
