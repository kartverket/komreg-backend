## Environment variables

### Må ha
- APP_DB_URL=r2dbc:postgresql://localhost:5433/komreg-db

Oppstart av lokal database.
```shell
docker run --rm -it -e POSTGRES_USER=regulering -e POSTGRES_PASSWORD=regulering -e POSTGRES_DB=komreg-db -p 5433:5432 postgres
```

### Vil ha for å kjøre lokalt
- APP_LOG_ENCODE=pattern

### OpenTelemetry
- OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318 (eller 4317 for gRPC)
- OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf (eller grpc)
- OTEL_METRICS_EXPORTER=otlp
- OTEL_TRACES_EXPORTER=otlp

Start Grafana med 
```shell
docker run -p 3000:3000 -p 4317:4317 -p 4318:4318 --rm -it grafana/otel-lgtm
```

## JOOQ
Jooq generer kode for å gjøre spørringer mot databasen. Den spinner opp en testcontainer for å gjøre dette fra flyway 
scripts, så går av seg selv.

## OpenAPI
Generert dynamisk runtime. Etter oppstart, se [her](http://localhost:8082/openapi)
