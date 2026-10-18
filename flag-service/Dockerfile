# Stage 1: Build the application using Maven
FROM maven:3-eclipse-temurin-21 AS build

WORKDIR /build

# Copy the Maven wrapper so the build uses the pinned Maven version
# (.mvn/wrapper/maven-wrapper.properties -> Maven 3.9.9) instead of the
# base image's own Maven. Both files must be present or ./mvnw cannot run.
COPY mvnw .
COPY .mvn .mvn

# Copy only pom.xml next so the dependency layer stays cached until it changes
COPY pom.xml .

# Copy source code
COPY src ./src

# Build the application (skip tests for image build).
# chmod guards against the executable bit being lost when the build context
# crosses a filesystem that does not preserve POSIX permissions.
RUN chmod +x mvnw && ./mvnw -B package -DskipTests

# Stage 2: Create runtime image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /build/target/*.jar app.jar

# Expose the application port
EXPOSE 8001

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
