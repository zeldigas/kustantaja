FROM eclipse-temurin:21.0.4_7-jre as build

ARG VERSION=""

ADD target/text2confl${VERSION}-dist/text2confl /opt/app

RUN find /opt/app -exec touch -t 200001010101 {} \;

FROM eclipse-temurin:21.0.4_7-jre

LABEL org.opencontainers.image.source="https://github.com/zeldigas/text2confl"

ARG PLANTUML="1.2025.0"

ADD https://github.com/plantuml/plantuml/releases/download/v$PLANTUML/plantuml.jar /usr/local/bin/plantuml.jar
ADD scripts/plantuml /usr/local/bin/plantuml
RUN chmod 755 /usr/local/bin/plantuml.jar /usr/local/bin/plantuml
ENV DIAGRAM_PLANTUML_CLASSPATH=/usr/local/bin/plantuml.jar

RUN apt-get update \
    && apt-get install -y graphviz \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /opt/app/lib /opt/text2confl/lib
COPY --from=build /opt/app/app /opt/text2confl/app
COPY --from=build /opt/app/text2confl /opt/text2confl/text2confl

RUN adduser -no-create-home --disabled-password user \
    && ln -s /opt/text2confl/text2confl /usr/local/bin/text2confl

USER user

ENTRYPOINT ["/opt/text2confl/text2confl"]
