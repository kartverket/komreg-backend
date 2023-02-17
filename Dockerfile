FROM gradle:7-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

FROM openjdk:17
EXPOSE 8080:8080
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*.jar /app/komreg-backend.jar
ENTRYPOINT ["java","-jar","/app/komreg-backend.jar"]