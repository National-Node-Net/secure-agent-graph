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

SAG_DIR=../..
USER_1_DATA="http://example/person4321"
USER_2_DATA="http://example/person9876"
SAG_SERVER=http://localhost:3031
USER_1="test+user+admin@ndtp.co.uk"
USER_2="test+user@ndtp.co.uk"

wait_for_url_auth () {
    echo "Testing $1 with auth..."
    printf 'GET %s\nAuthorization: bearer %s\nHTTP 200' "$1" $3 | hurl --retry "$2";# > /dev/null;
    return 0
}

echo "Starting auth test"
echo "Fetch id tokens"

ID_TOKEN_1=$(aws --endpoint http://0.0.0.0:9229 cognito-idp initiate-auth --client-id 6967e8jkb0oqcm9brjkrbcrhj --auth-flow USER_PASSWORD_AUTH --auth-parameters USERNAME=$USER_1,PASSWORD=password | jq -r '.AuthenticationResult.IdToken')
ID_TOKEN_2=$(aws --endpoint http://0.0.0.0:9229 cognito-idp initiate-auth --client-id 6967e8jkb0oqcm9brjkrbcrhj --auth-flow USER_PASSWORD_AUTH --auth-parameters USERNAME=$USER_2,PASSWORD=password | jq -r '.AuthenticationResult.IdToken')

wait_for_url_auth "$SAG_SERVER/ds" 60 $ID_TOKEN_1

hurl hurl/upload-data-auth.hurl  --variable SAG_SERVER=$SAG_SERVER --variable ID_TOKEN=$ID_TOKEN_1 || { docker compose down; exit 1; }

hurl hurl/sparql-auth-admin-user.hurl --variable SAG_SERVER=$SAG_SERVER \
--variable ID_TOKEN_USER_1=$ID_TOKEN_1 \
--variable ID_TOKEN_USER_2=$ID_TOKEN_2 \
--variable USER_1_DATA=$USER_1_DATA \
--variable USER_2_DATA=$USER_2_DATA || { docker compose down; exit 1; }

echo "Passed"

docker compose down

exit 0
