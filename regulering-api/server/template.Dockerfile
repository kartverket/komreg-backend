FROM dhi.io/eclipse-temurin:25-debian

EXPOSE @DEFAULT_HTTP_PORT@

WORKDIR /app

# Kopier med kjørende brukers rettigheter, ellers rare classloader problemer
# https://docs.docker.com/dhi/migration/checklist/ referer til UID 65532, så
# antar at det er en policy at dette er UID-en
COPY --chown=65532:65532 app/ /app/

CMD ["java", "--enable-native-access=ALL-UNNAMED", "-cp", "lib/*", "@MAIN_CLASS@"]
