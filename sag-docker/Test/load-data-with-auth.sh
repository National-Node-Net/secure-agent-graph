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

URL="http://localhost:8080/ds"

curl -XPOST --data-binary @data1.trig \
     --header 'Content-type: application/trig' \
     --header 'Authorization: bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IkNvZ25pdG9Mb2NhbCJ9.eyJjb2duaXRvOnVzZXJuYW1lIjoidGVzdCt1c2VyK2FkbWluQHRlbGljZW50LmlvIiwiYXV0aF90aW1lIjoxNzM3MzkxNjUwLCJlbWFpbCI6InRlc3QrdXNlcithZG1pbkB0ZWxpY2VudC5pbyIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwiZXZlbnRfaWQiOiJjMzQ1MDk5OC1kNGQxLTQyODgtOGMyYy04OTIxNmY1NTQwNzQiLCJpYXQiOjE3MzczOTE2NTAsImp0aSI6IjY2ZTE3Y2YwLWVjYTAtNGY5OS05NjkwLTUwZWJlNGQzYjlmMiIsInN1YiI6IjJiMGQyZDlhLTU5NWYtNDg4NC05MmIxLTQ0MjFlZGFjZTA3OSIsInRva2VuX3VzZSI6ImlkIiwiY29nbml0bzpncm91cHMiOlsidGNfYWRtaW4iLCJ0Y19yZWFkIl0sImV4cCI6MTczNzQ3ODA1MCwiYXVkIjoiNjk2N2U4amtiMG9xY205YnJqa3JiY3JoaiIsImlzcyI6Imh0dHA6Ly9hd3MtY29nbml0by1sb2NhbDo5MjI5L2xvY2FsXzZHTHVoeGhEIn0.RiUpQY7YUqD0jbKWCcrDTcIpV1XE_SElm1h8IJb-0Cq8sKFIRNO5-JuBGQnJjC-RZ2-mfomwVjaMTcdNXltxYsbJzz7Xn_Fp5dCFGeJ3I26eknC5IwEcjkaUmI6GhZrWXgBrXb1y_YUEhH4qpNhaTJSG-yUsd6Fzk9a9811cVHbN56d_L8U8EdPgUVl1i6QYwvJid4M0RImwf4chvJSdVf6FZSjsSx0NXq-dZZUPR3tehp6bq4iOWOtyEnEfAoTKACsjbq38ZEmR8iPeKTRssDbHJfq1V6BGSS1R2IelBeR41YTgZWAGTyFzerUUuu9Bm1GapfZzDMszPTN5_HnHpA' \
     $URL/upload
