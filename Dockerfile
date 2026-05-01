# Build
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
RUN apk add --no-cache bash
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src
# Normalize Windows line endings if present
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -B -DskipTests package && mv /app/target/swiftpay-api-0.0.1-SNAPSHOT.jar /app/app.jar

# Run
FROM eclipse-temurin:25-jre-alpine
RUN adduser -D -h /app -u 1001 appuser
WORKDIR /app
USER appuser
COPY --from=build /app/app.jar app.jar
EXPOSE 8080
# Render sets PORT; Spring reads server.port from PORT via application-prod
ENV JAVA_OPTS="-Xms128m -Xmx384m -XX:MaxMetaspaceSize=128m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
