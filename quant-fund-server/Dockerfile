FROM public.ecr.aws/docker/library/maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl tzdata \
    && addgroup -S quantfund \
    && adduser -S quantfund -G quantfund

WORKDIR /app
COPY --from=build /workspace/target/quant-fund-server-*.jar /app/app.jar

USER quantfund
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
