# ==================== Build ====================
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
RUN apk add --no-cache bash
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src
# Normalize Windows line endings if present
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -B -DskipTests package && mv /app/target/*-SNAPSHOT.jar /app/app.jar

# ==================== Run ====================
FROM eclipse-temurin:25-jre-alpine
RUN adduser -D -h /app -u 1001 appuser
WORKDIR /app
USER appuser
COPY --from=build /app/app.jar app.jar
EXPOSE 8083
ENV JAVA_OPTS="-Xms128m -Xmx384m -XX:MaxMetaspaceSize=256m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
