# Backend-Image für Render (Free-Instanz mit 512 MB RAM)

# ===== Build =====
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Abhängigkeiten zuerst laden, damit sie bei reinen Code-Änderungen im Cache bleiben
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests

# ===== Laufzeit =====
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --uid 10001 app
COPY --from=build /app/target/order-management-0.0.1-SNAPSHOT.jar app.jar
USER app

# Heap auf rund 300 MB begrenzen (wie zuvor auf dem 512-MB-Dyno), der Rest bleibt für Metaspace und Threads.
# Kompakte Objekt-Header sparen zusätzlich Heap.
ENV JAVA_TOOL_OPTIONS="-XX:+UseCompactObjectHeaders -XX:MaxRAMPercentage=60 -Xss512k -XX:+ExitOnOutOfMemoryError"

# Render setzt PORT; das Profil "cloud" übernimmt ihn als server.port
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
