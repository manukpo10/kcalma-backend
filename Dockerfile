# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw

# Warm the dependency cache in its own layer before copying source, so source-only
# changes don't invalidate the (slow) dependency download layer.
RUN ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B package -DskipTests

# Explode the jar into app/app.jar + app/lib/*.jar (faster startup than a fat jar).
RUN java -Djarmode=tools -jar target/app.jar extract

# ---- Run stage ----
FROM eclipse-temurin:21-jre AS run
WORKDIR /app

RUN groupadd --system kcalma && useradd --system --gid kcalma --uid 1001 kcalma
COPY --from=build /build/app/ ./
RUN chown -R kcalma:kcalma /app
USER kcalma

# 512 MB Render free-tier tuning: cap heap/metaspace/threads well below the container limit.
ENV JAVA_TOOL_OPTIONS="-Xmx200m -Xss512k -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=32m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"
ENV MALLOC_ARENA_MAX=2
ENV SERVER_TOMCAT_THREADS_MAX=8

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
