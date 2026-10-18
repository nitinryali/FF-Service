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
#
# Debian-based (glibc) on purpose — do not switch this to the -alpine variant.
# Alpine ships musl libc, whose resolver cannot fall back to TCP and does not
# implement the search-domain/ndots handling that glibc does. The MongoDB driver
# resolves the Atlas shard hostnames through the system resolver
# (InetAddress -> getaddrinfo); on musl that lookup can stall, so the driver
# never opens a socket and every server sits in state=CONNECTING with no
# exception until server selection times out — indistinguishable from a network
# block. The SRV/TXT lookups still succeed under musl because the driver does
# those in pure Java via JNDI, reading /etc/resolv.conf directly, which is why
# this failure masquerades as a firewall problem.
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /build/target/*.jar app.jar

# Documentation only — EXPOSE does not publish or route anything. The app binds
# SERVER_PORT when set, otherwise PORT (Render sets 10000), otherwise 8001.
EXPOSE 8001

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
