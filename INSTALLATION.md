# Installation
**Repository:** `secure-agent-graph`    
**Description:** `This file provides detailed installation steps, including required dependencies and configurations for Secure Agent Graph.` 

<!-- SPDX-License-Identifier: OGL-UK-3.0 -->

Secure Agent Graph (SAG) provides [SPARQL](https://www.w3.org/TR/sparql-overview/) access using the [SPARQL
protocol](https://www.w3.org/TR/sparql-protocol/) and [SPARQL Graph Store
Protocol](https://www.w3.org/TR/sparql-graph-store-protocol/) to RDF data with [ABAC data
security](https://github.com/National-Node-Net/rdf-abac/blob/main/docs/abac.md).

## Starting installation
1. Run:
```
mvn clean install
```
2. Start the app through either
- Docker or
- Java

## Starting the app using Docker
To launch a simple server with no authentication:
1. Go to the 'sag-docker' directory
2. Run the script:
```
./docker-run.sh --config config/config-abac-local.ttl
```
This will build the docker image containing the application, and mount the 'mnt' directories within.

 3. Build a Docker image using:
 ```
build-arg PROJECT_VERSION="<YOUR PROJECT VERSION>
```
This will build the docker image and mount it to a port of your choosing. Please see the example Docker compose file in the docker-compose directory. 

4. Load data into the Docker image: Run the load-data script within 'sag-docker/Test' directory (within a separate terminal window).
 
This will load sample data for testing purposes only - assuming you are running on port 3030.
 
## Starting the app using Java
Java application can be run using the following commands:
```
USER_ATTRIBUTES_URL=http://localhost:8091 JWKS_URL=disabled \                                                  
java \
-Dfile.encoding=UTF-8 \
-Dsun.stdout.encoding=UTF-8 \
-Dsun.stderr.encoding=UTF-8 \
-classpath "sag-server/target/classes:sag-system/target/classes:sag-docker/target/dependency/*" \
uk.gov.dbt.ndtp.secure.agent.graph.SecureAgentGraph \
--config sag-docker/mnt/config/config-abac-local.ttl
```
In the above, the 'JWKS_URL' is disabled but can be set to the JWKS url for your identity provider and 'USER_ATTRIBUTES_URL' referes to the address that the IA NODE access application is running on.

## Example configuration
Secure Agent Graph is configured using a Fuseki configuration file
([documentation](docs/configuration-secure-agent-graph.md)). There is an [example config.ttl](docs/config.ttl) file.

You can find further example configurations later under [Try It Out](#try-it-out).

## System Configuration
The following environment variables can be used to control Secure Agent Graph:

### `USER_ATTRIBUTES_URL`
This is the network location of [user attribute server](https://github.com/National-Node-Net/ianode-access) which also
includes the hierarchies management.

The URL value is a template including `{user}`. Example: `http://some-host/users/lookup/{user}`

### `JWKS_URL`
This specifies the JSON Web Key Set (JWKS) URL to use to obtain the public keys for verifying JSON Web Tokens (JWTs).
The value "disabled" turns off token verification.

## Build
Building Secure Agent Graph is a two-step process.

The java artifacts are built using the maven release plugin. When these are released, the docker container is
automatically built.

Check versions in `release-setup`.

### Build and release the secure agent graph maven artifacts

On branch `main`:

Edit and commit `release-setup` to set the correct versions.

```
source release-setup
```

This prints the dry-run command.  If you need to change this file, edit it, then simply source the file again.

Dry run:

```
mvn $MVN_ARGS -DdryRun=true release:clean release:prepare
```

and for real:

```
mvn $MVN_ARGS release:clean release:prepare
```

This updates the version number.  Our automated GitHub Actions pipelines handles publishing the release build to Maven
Central and Docker Hub.

After release, do `git pull` to sync local and remote git repositories.

To rebuild for update version for development:

```
mvn clean install
```

### About the Docker Container

The docker container is automatically built by github action on a release of the Secure Agent Graph jar artifacts.

In the docker container we have:

```
    /fuseki/logs/
    /fuseki/databases/
    /fuseki/config/
```

and configuration files go into host `mnt/config/`.

### Try it out! 

The provided script, [docker-run.sh](sag-docker/docker-run.sh), runs SAG in a docker container, with the contents of the
local [mnt/config](sag-docker/mnt/config) directory mounted into the newly generated docker image for ease of use.
Similarly, the [mnt/databases](sag-docker/mnt/databases) and [mnt/logs](sag-docker/mnt/logs) are moutned for easier
analysis.

#### Example configuration - *Default*

```bash
   sag-docker/docker-run.sh
```
Passing no parameters means that it will default to (`"--mem /ds"`)

It specifies an in-memory dataset at "/ds" which replays the "RDF" topic on start-up. It assumes that Kafka must be up
and running, prior to launch.

The Fuseki server is available at `http://localhost:3030/ds`.

#### Example configuration - *ABAC*
```bash
   sag-docker/docker-run.sh --config config/config-local-abac.ttl
```
This runs the server using the configuration file [config-abac-local.ttl](sag-docker/mnt/config/config-abac-local.ttl.
It specifies an in-memory dataset at `/ds` and that Attribute Based Access Control is enabled.

*Note:* See caveat below re: authentication.


#### Example configuration - *Kafka Replay* 

```bash
   sag-docker/docker-run.sh --config config/config-replay-abac.ttl
```
As this suggests, this runs server using the configuration file `config/config-replay-abac.ttl` 
or [config-replay-abac.ttl](sag-docker/mnt/config/config-replay-abac.ttl) as it's known locally. 

It specifies an in-memory dataset at "/ds" which replays the "RDF" topic on start-up. It assumes that Kafka must be up
and running, prior to launch.

The Fuseki server is available at `http://localhost:3030/ds`.

#### More advanced testing - d-run

Alternately, you can use the script `d-run` which will map the relevant config and database directories from the local
filesystem, pulling down the given image and running it directly (not in `-d` mode). It requires a number of environment
variables to be set as indicated in the script. 

It can be run with exactly the same configuration as docker-run.sh except with no default configuration if nothing is
provided.

#### Open Telemetry

Open Telemetry for SAG will be enabled if any environment variables with `OTEL` in the name are present at runtime.  If
this is not the case then the Open Telemetry Agent is not attached to the JVM and no metrics/traces will be exported.


© Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally attributed to the UK's Department for Business, Innovation, Science and Trade (BIST) as the governing entity.  
Licensed under the Open Government Licence v3.0.  

You can view the full license at:  
https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/
