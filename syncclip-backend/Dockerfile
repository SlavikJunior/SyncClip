# Образ соответствует §13.6 дизайна: glibc-база, multi-stage, непривилегированный пользователь.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Копируем только то, что нужно сборке: .env, .git и build/ в контекст не попадают (OPS-02).
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties settings-gradle.lockfile ./
COPY gradle ./gradle
COPY build-logic ./build-logic
COPY server ./server
RUN ./gradlew :server:app:installDist --no-daemon

FROM eclipse-temurin:21-jre
RUN useradd --create-home --uid 10001 app \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/server/app/build/install/app /app
USER app
EXPOSE 8080
ENTRYPOINT ["/app/bin/app"]
