#!/bin/sh
## SPDX-License-Identifier: Apache-2.0
## © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme
## and is legally attributed to the UK's Department for Business, Innovation, Science and Trade (BIST) as the governing entity.
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

# Requires hurl https://hurl.dev/docs/installation.html
# Requires jq (https://jqlang.org/download/)

source aws-cognito-get-token.sh $TEST_USER1 $TEST_USER1_PASSWORD
USER1_TOKEN=$CURRENT_NDTP_JWT

source aws-cognito-get-token.sh $TEST_USER2 $TEST_USER2_PASSWORD
USER2_TOKEN=$CURRENT_NDTP_JWT

hurl hurl/upload-data-auth.hurl  --variable SAG_SERVER=$SECURE_AGENT_GRAPH_URL --variable ID_TOKEN=$USER1_TOKEN || exit 1

hurl hurl/sparql-auth-admin-user.hurl --variable SAG_SERVER=$SECURE_AGENT_GRAPH_URL \
--variable ID_TOKEN_USER_1=$USER1_TOKEN \
--variable ID_TOKEN_USER_2=$ID_TOKEN_2 \
--variable USER_1_DATA=$TEST_DATA_PERSON_1 \
--variable USER_2_DATA=$TEST_DATA_PERSON_2 || exit 1

echo "Passed"
exit 0
