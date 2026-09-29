#!/bin/sh
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

# Runs in the secure-agent-graph container, with directory /fuseki/.
## This starts Fuseki.
## Command line arguments are passed from "docker run"

# env | sort

MAIN=uk.gov.dbt.ndtp.core.MainSecureAgentGraph
FUSEKI_LIB="${FUSEKI_DIR}/lib"

## All in one directory
FUSEKI_CP="$FUSEKI_LIB"'/*'

env | grep "OTEL" >/dev/null 2>&1
if [ $? -eq 0 ]; then
  export FUSEKI_FMOD_OTEL=true
  if [ -z "${OTEL_SERVICE_NAME}" ]; then
    export OTEL_SERVICE_NAME="secure-agent-graph"
  fi
  JAVA_OPTIONS="-javaagent:${FUSEKI_DIR}/agents/opentelemetry-javaagent.jar ${JAVA_OPTIONS}"
fi
echo "java" $JAVA_OPTIONS -cp "$FUSEKI_CP" $MAIN "$@"
exec "java" $JAVA_OPTIONS -cp "$FUSEKI_CP" $MAIN "$@"
