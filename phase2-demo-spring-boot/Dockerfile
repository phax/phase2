# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY . /src
RUN mvn -DskipTests -pl phase2-demo-spring-boot -am install
# The Spring Boot plugin is declared without an <executions> block in the pom,
# so its repackage goal doesn't auto-bind. Spring Boot 4.x also requires
# 'package' and 'spring-boot:repackage' in the SAME mvn invocation, so chain
# them on just the demo module (deps already installed above).
RUN mvn -DskipTests -pl phase2-demo-spring-boot package spring-boot:repackage
# Pick the fat jar (excluding sources/javadoc/original) and stage it at a
# fixed path so the runtime stage can COPY a single file.
RUN set -eux; \
    jar=$(ls /src/phase2-demo-spring-boot/target/phase2-demo-spring-boot-*.jar \
          | grep -Ev 'sources|javadoc|original' | head -1); \
    test -n "$jar" || (echo "Spring Boot repackage did not produce a fat jar"; exit 1); \
    cp "$jar" /app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
RUN mkdir -p /app/config /app/data
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
