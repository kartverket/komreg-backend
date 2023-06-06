FROM eclipse-temurin:17-alpine
RUN apk --no-cache add libgcc libstdc++
EXPOSE 8080:8080
RUN apt-get update && \
    apt-get upgrade -y libssl3 && \
    apt-get clean && \
    mkdir /app
WORKDIR .
COPY build/libs/*.jar /app/komreg-backend.jar
ENTRYPOINT ["java","-jar","/app/komreg-backend.jar"]
