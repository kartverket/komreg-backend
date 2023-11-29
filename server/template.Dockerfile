FROM eclipse-temurin:17-alpine@sha256:4f6f61ededa179586bd6679bb23c448bad317ca352ee253b6359650923e86c9a

RUN apk update && apk upgrade
RUN apk --no-cache add libgcc libstdc++

RUN addgroup -g 1000 app && adduser -u 1000 -s /sbin/nologin -D -H -G app app
RUN mkdir /app && chown -R app:app /app
USER app

WORKDIR /app
COPY app/ /app/
EXPOSE 8080:8080
ENTRYPOINT ["@START_SCRIPT@"]
