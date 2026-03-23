FROM eclipse-temurin:17-alpine@sha256:9a78c6372b1e52020dbebdbff7ff2c73ce0cc8600e21cf94b08691a3421a33ef

RUN apk update && apk upgrade
RUN apk --no-cache add libgcc libstdc++

RUN addgroup -g 1000 app && adduser -u 1000 -s /sbin/nologin -D -H -G app app
RUN mkdir /app && chown -R app:app /app
USER app

WORKDIR /app
COPY app/ /app/
EXPOSE 8080:8080
ENTRYPOINT ["@START_SCRIPT@"]
