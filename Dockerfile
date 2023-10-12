FROM eclipse-temurin:21-alpine@sha256:0590276e28eadad32040b43c6564a991a3860049155be0c69e7d3632ead05f66
RUN apk update && apk upgrade
RUN apk --no-cache add libgcc libstdc++
EXPOSE 8080:8080
RUN mkdir /app
WORKDIR .
COPY build/libs/*.jar /app/komreg-backend.jar
ENTRYPOINT ["java","-jar","/app/komreg-backend.jar"]
