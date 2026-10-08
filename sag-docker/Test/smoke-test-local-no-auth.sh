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
# requires cognito to be running
# Debugging end process if tests fail: kill -9 $(lsof -ti:3030)

echo "Starting no auth test"

wait_for_url () {
    echo "Testing $1..."
    printf 'GET %s\nHTTP 200' "$1" | hurl --retry "$2" > /dev/null;
    return 0
}

SAG_DIR=../..
USER_1_DATA="http://example/person4321"
USER_2_DATA="http://example/person9876"
SAG_SERVER=http://localhost:3030
USER_1="test+user+admin@ndtp.co.uk"
USER_2="test+user@ndtp.co.uk"

wait_for_url "$SAG_SERVER/ds" 60
hurl hurl/upload-data-no-auth.hurl --variable SAG_SERVER=$SAG_SERVER || { docker compose down; exit 1; }
hurl hurl/sparql-no-auth.hurl --variable SAG_SERVER=$SAG_SERVER --variable USER_1_DATA=$USER_1_DATA --variable USER_2_DATA=$USER_2_DATA || { docker compose down; exit 1; }
hurl hurl/sqarql-text-no-auth.hurl --variable SAG_SERVER=$SAG_SERVER --variable USER_1_DATA=$USER_1_DATA --variable USER_2_DATA=$USER_2_DATA || { docker compose down; exit 1; }

echo "Passed"

docker compose down

exit 0
