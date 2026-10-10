FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /build
COPY app/pom.xml ./pom.xml
RUN mvn -B -ntp dependency:go-offline
COPY app/src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends git curl openssh-client ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 blog \
    && useradd --uid 10001 --gid blog --create-home blog \
    && git config --system --add safe.directory /vault
WORKDIR /app
COPY --from=build --chown=blog:blog /build/target/app.jar /app/app.jar
USER blog
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
