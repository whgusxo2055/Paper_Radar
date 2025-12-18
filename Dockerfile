FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./

COPY src src

RUN chmod +x gradlew \
    && ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar /app/app.jar

ENTRYPOINT ["java","-jar","/app/app.jar"]
