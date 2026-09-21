# Etapa 1: Construcción con Maven y JDK 21
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copia pom.xml y precarga dependencias
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copia código fuente y compila el JAR sin tests
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa 2: Imagen de ejecución ligera con JRE 21
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copia del artefacto JAR generado
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
