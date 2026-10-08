# Image du serveur FasoGuardian : construction Maven puis exécution sur un JRE 21 sans privilèges.
FROM eclipse-temurin:21-jdk AS construction
WORKDIR /source
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S fasoguardian && adduser -S fasoguardian -G fasoguardian
USER fasoguardian
WORKDIR /application
COPY --from=construction /source/target/fasoguardian-backend-*.jar serveur.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "serveur.jar"]
