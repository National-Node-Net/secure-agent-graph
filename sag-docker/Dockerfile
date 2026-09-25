## SPDX-License-Identifier: Apache-2.0
## Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin Programme.
  ##
  ##  Copyright (c) Telicent Ltd.
  ##
  ##  Licensed under the Apache License, Version 2.0 (the "License");
  ##  you may not use this file except in compliance with the License.
  ##  You may obtain a copy of the License at
  ##
  ##      http://www.apache.org/licenses/LICENSE-2.0
  ##
  ##  Unless required by applicable law or agreed to in writing, software
  ##  distributed under the License is distributed on an "AS IS" BASIS,
  ##  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ##  See the License for the specific language governing permissions and
  ##  limitations under the License.
  ##
  ##
  ##  Modifications made by the National Digital Twin Programme (NDTP)
  ##  © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme
  ##  and is legally attributed to the UK's Department for Business, Innovation, Science and Trade (BIST) as the governing entity.

## Dockerfile for Secure Agent Graph

# syntax=docker/dockerfile:1.7


ARG JAVA_VERSION=21

FROM eclipse-temurin:${JAVA_VERSION} AS secure-agent-graph

ARG FUSEKI_DIR=/fuseki

RUN apt-get update && \
    apt-get install -y --no-install-recommends dumb-init && \
    apt-get upgrade -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR $FUSEKI_DIR

RUN groupadd -g 1001 fuseki && useradd -m -u 1001 -g 1001 fuseki

ARG LOGS=${FUSEKI_DIR}/logs
ARG DATA=${FUSEKI_DIR}/databases
ARG CONFIGS=${FUSEKI_DIR}/config
ARG LIB=${FUSEKI_DIR}/lib
ARG AGENTS=${FUSEKI_DIR}/agents
ARG SBOMS=/opt/ianode/sbom

COPY sag-docker/entrypoint.sh .
COPY sag-docker/logback.xml .

RUN \
    mkdir -p $LOGS $DATA $CONFIGS $LIB $AGENTS $SBOMS && \
    chown -R fuseki ${FUSEKI_DIR} && \
    chmod a+x entrypoint.sh

# Build location of the jars we need.
ARG PROJECT_VERSION
ARG FUSEKI_JAR

## Collected jars
ARG JARS=sag-docker/target/dependency
COPY ${JARS} lib
ARG AGENTS=sag-docker/target/agents
COPY ${AGENTS} agents
ARG SBOM=sag-docker/target/docker-${PROJECT_VERSION}-bom.json
COPY ${SBOM} /opt/ianode/sbom/secure-agent-graph-${PROJECT_VERSION}-bom.json

## Default environment variables.
ENV \
    JAVA_OPTIONS="-Xmx2048m -Xms2048m"  \
    FUSEKI_JAR="${FUSEKI_JAR}"          \
    FUSEKI_DIR="${FUSEKI_DIR}"          \
    ROCKSDB_MUSL_LIBC="false"            \
    LOG_CONFIG_FILE="/fuseki/logback.xml"

USER fuseki

EXPOSE 3030

ENTRYPOINT [ "/usr/bin/dumb-init", "-v", "--single-child", "--", "./entrypoint.sh" ]
CMD []
