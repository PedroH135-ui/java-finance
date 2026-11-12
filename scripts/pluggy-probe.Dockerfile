FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /probe-build
COPY backend/ ./
RUN mvn -B -ntp -DskipTests test-compile dependency:copy-dependencies \
    -DincludeArtifactIds=jackson-databind,jackson-core,jackson-annotations,spring-web,spring-core,spring-jcl,slf4j-api,logback-classic,logback-core \
    -DoutputDirectory=/probe-libs

FROM eclipse-temurin:21-jre-jammy
WORKDIR /probe
RUN groupadd --system probe && useradd --system --gid probe probe
COPY --from=build /probe-build/target/classes/ /probe/classes/
COPY --from=build /probe-build/target/test-classes/ /probe/classes/
COPY --from=build /probe-libs/ /probe/lib/
ENV PORT=8080
USER probe
EXPOSE 8080
ENTRYPOINT ["java","-cp","/probe/classes:/probe/lib/*","br.com.javafinance.banking.PluggyReadProbeServer"]
