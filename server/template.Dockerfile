FROM eclipse-temurin:25-alpine@sha256:09349d79941fd53bb3d487b393ca118d8853c08c09193f416fe6a8718df9e732

RUN apk update && apk upgrade
RUN apk --no-cache add libgcc libstdc++

RUN addgroup -g 1000 app && adduser -u 1000 -s /sbin/nologin -D -H -G app app
RUN mkdir /app && chown -R app:app /app
USER app

WORKDIR /app
COPY app/ /app/
EXPOSE 8080:8080
ENTRYPOINT ["@START_SCRIPT@"]
