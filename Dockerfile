FROM eclipse-temurin:20-alpine
EXPOSE 8080:8080
RUN mkdir /app
WORKDIR .
COPY build/libs/*.jar /app/komreg-backend.jar
ENTRYPOINT ["java","-jar","/app/komreg-backend.jar"]
