FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B || true
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S dataflow && adduser -S dataflow -G dataflow
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
USER dataflow
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=mysql"]
