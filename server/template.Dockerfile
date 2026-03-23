FROM eclipse-temurin:25-alpine@sha256:da683f4f02f9427597d8fa162b73b8222fe08596dcebaf23e4399576ff8b037e

RUN apk update && apk upgrade
RUN apk --no-cache add libgcc libstdc++

RUN addgroup -g 1000 app && adduser -u 1000 -s /sbin/nologin -D -H -G app app
RUN mkdir /app && chown -R app:app /app
USER app

WORKDIR /app
COPY app/ /app/
EXPOSE 8080:8080
ENTRYPOINT ["@START_SCRIPT@"]
