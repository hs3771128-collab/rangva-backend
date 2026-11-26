# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /build

# Dependencies first so Docker can cache this layer between deploys.
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package \
    && JAR=$(find target -maxdepth 1 -name '*.jar' ! -name '*.original' | head -n 1) \
    && cp "$JAR" app.jar

# ---------- runtime ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /build/app.jar app.jar
# Product images are served from local disk (WebMvcConfig maps /images/**).
COPY images ./images

RUN chown -R app:app /app && chmod -R 777 /app/images
USER app

EXPOSE 8080

# Free instances usually have 512 MB RAM: keep the JVM footprint small.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=45 -XX:MaxMetaspaceSize=128m -Xss512k -XX:+UseSerialGC"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
