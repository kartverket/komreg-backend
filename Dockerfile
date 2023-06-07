FROM eclipse-temurin:17-alpine@sha256:4f6f61ededa179586bd6679bb23c448bad317ca352ee253b6359650923e86c9a
RUN apk update && apk upgrade
RUN apk --no-cache add libgcc libstdc++
EXPOSE 8080:8080
RUN mkdir /app
WORKDIR .
COPY build/libs/*.jar /app/komreg-backend.jar
ENTRYPOINT ["java","-jar","/app/komreg-backend.jar"]
