FROM eclipse-temurin:17-alpine
RUN apk update && apk upgrade
RUN apk --no-cache add libgcc libstdc++
EXPOSE 8080:8080
RUN mkdir /app
WORKDIR .
COPY build/libs/*.jar /app/komreg-backend.jar
ENTRYPOINT ["java","-jar","/app/komreg-backend.jar"]
