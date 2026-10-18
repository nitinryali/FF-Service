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
#
# MAVEN_CONFIG is cleared for this call, and it must be. The official Maven
# image sets MAVEN_CONFIG=/root/.m2, and the Maven wrapper forwards that
# variable straight through as a command-line argument:
#
#   mvnw:  ${WRAPPER_LAUNCHER} $MAVEN_CONFIG "$@"
#
# so Maven receives "/root/.m2" as a lifecycle phase and dies with
# 'Unknown lifecycle phase "/root/.m2"'. It only appears inside this image —
# on a host where MAVEN_CONFIG is unset the variable expands to nothing.
#
# chmod guards against the executable bit being lost when the build context
# crosses a filesystem that does not preserve POSIX permissions.
RUN chmod +x mvnw && MAVEN_CONFIG= ./mvnw -B package -DskipTests

# Stage 2: Create runtime image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /build/target/*.jar app.jar

# Documentation only — EXPOSE does not publish or route anything. The app binds
# SERVER_PORT when set, otherwise PORT (Render sets 10000), otherwise 8001.
EXPOSE 8001

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
