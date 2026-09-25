# Uninstall
**Repository:** `secure-agent-graph`    
**Description:** `This file provides detailed steps to remove this repository, including any dependencies and configurations for Secure Agent Graph.`  

<!-- SPDX-License-Identifier: OGL-UK-3.0 -->

Uninstalling and removing the repository involves 3 parts:
- Java installation and configuration
- Docker configuration and containers
- Delete repository clone

## Removing the Java installation 
Run:
```
mvn clean
mvn dependency:purge-local-repository -DmanualInclude="uk.gov.dbt.ndtp.secure-agent.graph
```

## Deleting Docker containers
1. List all containers to find the container ID or name:
```
docker ps -a
```
2. Stop the Docker container by ID or name if it is running:
```
docker stop <container_id_or_name>
```
   
3. Delete the Docker container by ID or name:
```
docker rm <container_id_or_name>
```
You can also use the -f flag to forcefully remove a running Docker container:
```
docker rm -f <container_id_or_name>
```
4. Confirm Docker has been deleted by listing all Docker containers:
```   
docker ps -a
```

## Deleting repository clone
Simply delete the cloned repository files from working location.

© Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally attributed to the UK's Department for Business, Innovation, Science and Trade (BIST) as the governing entity.  
Licensed under the Open Government Licence v3.0.  

You can view the full license at:  
https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/
