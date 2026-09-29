# Testing
**Repository:** `secure-agent-graph`  
**Description:** `This file describes some testing for the Secure Agent Graph container.`  
<!-- SPDX-License-Identifier: OGL-UK-3.0 -->

## Testing the container without Kafka

Set-up: in-memory DB, local attribute store, no Kafka connector
with test data in `Test/`

```bash
./docker-run.sh --config config/config-abac-local.ttl
```

## Client calls:

```
URL="http://localhost:3030/ds"
```

Some basic tokens:

```
# user: user1
TOKEN="VW5zZXQ.eyBlbWFpbDogInVzZXIxIn0.VW5zZXQ"
```
(one result)

```
# user: employee
TOKEN="VW5zZXQ.eyBlbWFpbDogImVtcGxveWVlIn0.VW5zZXQ"
```
(no attributes - rejected)

```
# user: contractor
TOKEN="VW5zZXQ.eyBlbWFpbDogImNvbnRyYWN0b3IifQ.VW5zZXQ"
(no attributes - rejected)
```

```
   curl -XPOST --data-binary @Test/data1.trig \
        --header 'Content-type: application/trig' \
        $URL/upload
```
```
   curl --header "x-amzn-oidc-data: Bearer: $TOKEN" \
        -d query="SELECT * { ?s ?p ?o}"
$URL/sparql
```

## Testing with Kafka
This assumes you have the pre-requisite Kafka instance up and running.
```bash
   ./docker-run.sh --config config/config-replay-abac.ttl
```

## Smoke Testing

This section covers running the `smoke-test-local.sh` script in `secure-agent-graph`.

1. Smoke tests are located in `sag-docker/Test`, change directory into this from the `secure-agent-graph` base directory:
   ```shell
   cd sag-docker/Test
   ```

2. These smoke tests require the [hurl](https://hurl.dev/) library for request assertions and [jq]() for json processing.
   Which can be downloaded with the following commands:
   ```shell
   brew install hurl
   ```
   &
   ```shell
   brew install jq
   ```

3. Start the smoke test containers by using the following command:
   ```shell
   docker compose up -d
   ```

4. Start the authentication smoke test using:
   ```shell
   ./smoke-test-local-auth.sh
   ```

5. Start the no authentication smoke test using:
   ```shell
   ./smoke-test-local-no-auth.sh
   ```

6. Ensure the smoke tests have passed by seeing a `Passed` message printed in terminal and `secure-agent-graph` response:
   ```plaintext
   ... Rest of Response ...
      }
   }
   -----------------------------------------------------------------------------------------------------------------------------------
   | s                           | p                                                 | o                                             |
   ===================================================================================================================================
   | <http://example/person9876> | <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> | <http://ies.data.gov.uk/ontology/ies4#Person> |
   | <http://example/person9876> | <http://www.w3.org/2000/01/rdf-schema#label>      | "Smith"                                       |
   | <http://example/person9876> | <http://example/empId>                            | 9876                                          |
   | <http://example/person9876> | <http://example/phone>                            | "0777 11 11 11"                               |
   | <http://example/person9876> | <http://example/phone>                            | "0777 22 22 22"                               |
   | <http://example/person4321> | <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> | <http://ies.data.gov.uk/ontology/ies4#Person> |
   | <http://example/person4321> | <http://www.w3.org/2000/01/rdf-schema#label>      | "Jones"                                       |
   | <http://example/person4321> | <http://example/empId>                            | 4321                                          |
   | <http://example/person4321> | <http://example/phone>                            | "0400 111 222"                                |
   | <http://example/person4321> | <http://example/phone>                            | "0400 111 333"                                |
   -----------------------------------------------------------------------------------------------------------------------------------
   Passed
   ```

## Caveats

### Authentication
By default, this is disabled (via `JWK_URL=disabled`) which you will need to edit from the scripts.

## Previous testing script
The previous script,`d-run`, still exists which does the same as the above and is subject to the same caveats.

---
© Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally attributed to the UK's Department for Business, Innovation, Science and Trade (BIST) as the
governing entity.

Licensed under the Open Government Licence v3.0.
