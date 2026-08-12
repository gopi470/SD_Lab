
# # Stage 1: Build the Spring Boot application
# FROM maven:3.9-eclipse-temurin-17 AS build

# WORKDIR /app

# COPY pom.xml .
# COPY src ./src

# RUN mvn clean package -DskipTests

# # Stage 2: Run the application
# FROM eclipse-temurin:17-jre

# WORKDIR /app

# COPY --from=build /app/target/*.jar app.jar

# EXPOSE 8082

# ENTRYPOINT ["java", "-jar", "app.jar"]
# Base image with Java 17 and Maven
#-----------------------------------------------------------------------
# FROM ubuntu:24.04

# ENV DEBIAN_FRONTEND=noninteractive

# # Install Java, Maven, Redis and required tools
# RUN rm -rf /var/lib/apt/lists/* && \
#     apt-get clean && \
#     apt-get update -o Acquire::By-Hash=false && \
#     apt-get install -y \
#         openjdk-17-jdk \
#         maven \
#         redis-server \
#         wget \
#         gnupg \
#         ca-certificates && \
#     rm -rf /var/lib/apt/lists/*

# # Add MongoDB repository
# RUN wget -qO- https://www.mongodb.org/static/pgp/server-8.0.asc | \
#     gpg --dearmor -o /usr/share/keyrings/mongodb-server-8.0.gpg && \
#     echo "deb [ arch=amd64,arm64 signed-by=/usr/share/keyrings/mongodb-server-8.0.gpg ] https://repo.mongodb.org/apt/ubuntu noble/mongodb-org/8.0 multiverse" \
#     > /etc/apt/sources.list.d/mongodb-org-8.0.list && \
#     apt-get update -o Acquire::By-Hash=false && \
#     apt-get install -y mongodb-org && \
#     rm -rf /var/lib/apt/lists/*

# WORKDIR /app

# COPY pom.xml .
# COPY src ./src

# RUN mvn clean package -DskipTests

# COPY start.sh /start.sh
# RUN chmod +x /start.sh

# EXPOSE 8082 27017 6379

# ENTRYPOINT ["/start.sh"]



# MongoDB is already included in this base image
FROM debian:bookworm

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && \
    apt-get install -y \
        openjdk-17-jdk \
        maven \
        redis-server \
        wget \
        gnupg \
        ca-certificates && \
    wget -qO- https://www.mongodb.org/static/pgp/server-7.0.asc | \
    gpg --dearmor -o /usr/share/keyrings/mongodb-server.gpg && \
    echo "deb [ signed-by=/usr/share/keyrings/mongodb-server.gpg ] https://repo.mongodb.org/apt/debian bookworm/mongodb-org/7.0 main" \
    > /etc/apt/sources.list.d/mongodb-org.list && \
    apt-get update && \
    apt-get install -y mongodb-org && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

COPY start.sh /start.sh

RUN chmod +x /start.sh

EXPOSE 8082 27017 6379

ENTRYPOINT ["/start.sh"]