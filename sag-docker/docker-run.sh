#!/bin/bash
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

# Consolidated script for building and running Docker containers for Secure Agent Graph
# Pass in "alpine" as a parameter to create the Alpine image

# Acquire the current directory in case we are running from project root or the local directory
CURRENT_DIR=$(dirname $0)

# Function for getting version number from the repo pom file
function get_version() {
  local version=$(mvn -q -f pom.xml -Dexec.executable=echo -Dexec.args="\${$1}" --non-recursive exec:exec)
  echo "$version"
}

# Fetch the project and Fuseki versions
PROJECT_VERSION=$(get_version project.version)
FUSEKI_SERVER_VERSION=$(get_version ver.fuseki-server)

# Check if versions were successfully extracted
if [[ -z "$PROJECT_VERSION" || -z "$FUSEKI_SERVER_VERSION" ]]; then
    echo "Error: Could not extract version information from POM file"
    exit 1
fi

# Find the relevant Fuseki JAR file from the ./target/dependency directory
FUSEKI_JAR=$(find ${CURRENT_DIR}/target/dependency -name "jena-fuseki-main-${FUSEKI_SERVER_VERSION}.jar" | head -n 1)

# Check if the specific version was found, otherwise find any available jena-fuseki-main JAR
if [[ -z "$FUSEKI_JAR" ]]; then
    FUSEKI_JAR=$(find ${CURRENT_DIR}/target/dependency -name "jena-fuseki-main-*.jar" | head -n 1)
fi

# Check if a JAR file was found
if [[ -z "$FUSEKI_JAR" ]]; then
    echo "Error: Could not find jena-fuseki-main JAR file in ./target/dependency"
    exit 1
fi
# Extract the JAR file name
FUSEKI_JAR_NAME=$(basename "$FUSEKI_JAR")

# Select the Dockerfile to use (default or alpine)
DOCKERFILE="Dockerfile"
IMAGE_TAG="secure-agent-graph:${PROJECT_VERSION}"
if [[ "$1" == "alpine" ]]; then
    DOCKERFILE="Dockerfile.alpine"
    IMAGE_TAG="secure-agent-graph:${PROJECT_VERSION}-alpine"
    shift # Remove the 'alpine' argument from the $@
fi

# Build the Docker image
docker build --build-arg FUSEKI_JAR="${FUSEKI_JAR_NAME}" \
             --build-arg PROJECT_VERSION="${PROJECT_VERSION}" \
             -t $IMAGE_TAG -f ${CURRENT_DIR}/${DOCKERFILE} ${CURRENT_DIR}/..

# Remove any existing container with the same name
EXISTING_CONTAINER=$(docker ps -a -q -f name=secure-agent-graph-container)
if [ -n "$EXISTING_CONTAINER" ]; then
    docker stop secure-agent-graph-container 2>/dev/null
    docker rm secure-agent-graph-container 2>/dev/null
fi

# Check if $@ is empty and set it to a default value (in memory database with a /ds dataset)
ARGS="$@"
if [[ -z "$ARGS" ]]; then
    ARGS="--mem /ds"
fi

# Prepare the mount directory for logs, databases, and config
MNT_DIR=$(pwd)/${CURRENT_DIR}/mnt

# Run the Docker container, mapping port 3030, setting the necessary environment variables
docker run -d \
    -e JAVA_OPTIONS="-Xmx2048m -Xms2048m" \
    -e JWKS_URL="disabled" \
    -p 3030:3030 \
    -v "$MNT_DIR/logs:/fuseki/logs"      \
    -v "$MNT_DIR/databases:/fuseki/databases" \
    -v "$MNT_DIR/config:/fuseki/config"  \
    --name secure-agent-graph-container \
    $IMAGE_TAG $ARGS
