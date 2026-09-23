FROM node:24-alpine AS frontend
WORKDIR /web
COPY frontend/package*.json ./
RUN npm ci --no-fund --no-audit
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-17 AS backend
WORKDIR /build
COPY backend/pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /web/dist ./src/main/resources/static
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system votacao && useradd --system --gid votacao --home-dir /app --no-create-home --shell /usr/sbin/nologin votacao
WORKDIR /app
COPY --from=backend --chown=votacao:votacao /build/target/votacao.jar ./votacao.jar
USER votacao
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/votacao.jar"]
