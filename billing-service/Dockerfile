FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Step 1
# Собрать модуль contracts, чтобы он был в локальном репозитории контейнера
COPY contracts/pom.xml ./contracts/pom.xml
COPY contracts/src ./contracts/src
RUN mvn -f contracts/pom.xml clean install -DskipTests

# Кэширование зависимостей сервиса
COPY billing-service/pom.xml ./billing-service/pom.xml
RUN mvn -f billing-service/pom.xml dependency:go-offline

# Собрать сервис
COPY billing-service/src ./billing-service/src
RUN mvn -f billing-service/pom.xml clean package -DskipTests

# Step 2
# Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
WORKDIR /app
COPY --from=builder /app/billing-service/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]