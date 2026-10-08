#!/bin/bash
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

NDTP_USER_POOL_ID=$(aws cognito-idp list-user-pools --region $AWS_REGION --max-results 10 --query "UserPools[?Name=='ndtp-testing'] | [0]" | jq -r .Id)

CLIENT_ID=$(aws cognito-idp list-user-pool-clients --region $AWS_REGION --user-pool-id $NDTP_USER_POOL_ID --query "UserPoolClients[?ClientName=='ndtp-testing-client']|[0]" | jq -r .ClientId)
CLIENT_SECRET=$(aws cognito-idp describe-user-pool-client --region $AWS_REGION --user-pool-id $NDTP_USER_POOL_ID --client-id $CLIENT_ID | jq -r .UserPoolClient.ClientSecret)

SECRET_HASH=$(echo -n "$1$CLIENT_ID" | openssl dgst -sha256 -hmac $CLIENT_SECRET -binary | openssl enc -base64)

export CURRENT_NDTP_JWT=$(aws --endpoint https://cognito-idp.$AWS_REGION.amazonaws.com cognito-idp initiate-auth --client-id $CLIENT_ID --auth-flow USER_PASSWORD_AUTH --auth-parameters "USERNAME=$1,PASSWORD=$2,SECRET_HASH=$SECRET_HASH" | jq -r .AuthenticationResult.IdToken)
