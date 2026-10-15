FROM node:24.14.0-alpine AS web
WORKDIR /web
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21 AS backend
WORKDIR /build/backend
COPY backend/pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY backend/ .
COPY --from=web /web/dist /build/frontend/dist
RUN mvn -B -ntp -Pintegrated -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system finance && useradd --system --gid finance finance
COPY --from=backend /build/backend/target/java-finance-0.1.0.jar app.jar
USER finance
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

