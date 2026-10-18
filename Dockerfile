# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY config/ config/
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src/ src/
# Quality gates run in CI via ./mvnw verify; the image build only packages.
RUN ./mvnw -q -B -DskipTests -Dcheckstyle.skip=true -Dspotbugs.skip=true -Djacoco.skip=true package \
    && mv target/storeops-*.jar target/app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home storeops
USER storeops
WORKDIR /app
COPY --from=build /workspace/target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
