FROM eclipse-temurin:25-alpine@sha256:3fd2d245c4e0eba615fe366a71b8bd25f5db7104f53e4026b24bf508b880bd2a

RUN apk update && apk upgrade
RUN apk --no-cache add libgcc libstdc++

RUN addgroup -g 1000 app && adduser -u 1000 -s /sbin/nologin -D -H -G app app
RUN mkdir /app && chown -R app:app /app
USER app

WORKDIR /app
COPY app/ /app/
EXPOSE 8080:8080
ENTRYPOINT ["@START_SCRIPT@"]
