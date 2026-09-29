# README  

**Repository:** `secure-agent-graph`  
**Description:** `This repository provides the libraries and mechanism for receiving and holding RDF (Resource Description Framework) data. It also provides SPARQL access and integration points such as with ABAC components for security and control over data access.` 

<!-- SPDX-License-Identifier: Apache-2.0 AND OGL-UK-3.0 -->

## Overview  
This repository contributes to the development of **secure, scalable, and interoperable data-sharing infrastructure**. It supports NDTP’s mission to enable **trusted, federated, and decentralised** data-sharing across organisations.  

This repository is one of several open-source components that underpin NDTP’s **Integration Architecture (IA)**—a framework designed to allow organisations to manage and exchange data securely while maintaining control over their own information. The IA is actively deployed and tested across multiple sectors, ensuring its adaptability and alignment with real-world needs. 

For a complete overview of the Integration Architecture (IA) project, please see the [Integration Architecture Documentation](https://github.com/National-Node-Net/integration-architecture-documentation).

## Prerequisites  
Before using this repository, ensure you have the following dependencies installed:  
- **Required Tooling:** 
    - Java 21
    - Github PAT token set to allow retrieval of maven packages from Github Packages
    - Docker (if running via docker)
- **Pipeline Requirements:** 
    - Cloud platform credentials
- **Supported Kubernetes Versions:** N/A
- **System Requirements:** 
    - Java 21
    - Docker
    - Kafka (or connectivity to) - if applicable

## Quick Start  
Follow these steps to get started quickly with this repository. For detailed installation, configuration, and deployment, refer to the relevant MD files.  

### 1. Download and Build  
```sh  
git clone https://github.com/National-Node-Net/secure-agent-graph.git
cd [secure-agent-graph]  
```
### 2. Run Build Version  
```sh  
mvn clean install --version  

```

### 3. Full Installation  
Refer to [INSTALLATION.md](INSTALLATION.md) for detailed installation steps, including required dependencies and setup configurations.  

### 4. Testing
For information on testing `secure-agent-graph` by running its smoke tests, see: [TESTING.md](/sag-docker/TESTING.md)

### 5. Uninstallation  
For steps to remove this repository and its dependencies, see [UNINSTALL.md](UNINSTALL.md).  

## Features  
- **Key functionality** 
    - Supports secure and RDF (Resource Description Framework) data-sharing.
    - Implements [ABAC (Attribute-Based Access Control)](https://github.com/National-Node-Net/rdf-abac/blob/main/docs/abac.md) data security.
- **Key integrations** 
    - Provides SPARQL access using the SPARQL protocol and SPARQL Graph Store Protocol.
    - Integrates with Apache Jena Fuseki server.
    - Includes Fuseki-Kafka bridge for Kafka integration.
    - Offers [GraphQL]((https://github.com/National-Node-Net/graphql-jena/blob/main/docs/index.md)) API interfaces.
- **Scalability & performance** 
    - Optimised for high-throughput environments.
    - Supports in-memory datasets for fast data access.
- **Modularity** 
    - Designed with a plugin-based architecture for extensibility.
    - Configurable using Fuseki configuration files and environment variables.

## Testing Guide

### Running Unit Tests
Navigate to the root of the project and run `mvn test` to run the tests for the repository.

## API Documentation  
Documentation detailing the relevant configuration and endpoints is provided [here](docs/configuration-secure-agent-graph.md ). 


## Public Funding Acknowledgment  
This repository has been developed with public funding as part of the National Digital Twin Programme (NDTP), a UK Government initiative. NDTP, alongside its partners, has invested in this work to advance open, secure, and reusable digital twin technologies for any organisation, whether from the public or private sector, irrespective of size.  

## License  
This repository contains both source code and documentation, which are covered by different licenses:  
- **Code:** Originally developed by Telicent UK Ltd, now maintained by National Digital Twin Programme. Licensed under the [Apache License 2.0](LICENSE.md).  
- **Documentation:** Licensed under the [Open Government Licence (OGL) v3.0](OGL_LICENSE.md).  

By contributing to this repository, you agree that your contributions will be licensed under these terms.

See [LICENSE.md](LICENSE.md), [OGL_LICENSE.md](OGL_LICENSE.md), and [NOTICE.md](NOTICE.md) for details.  

## Security and Responsible Disclosure  
We take security seriously. If you believe you have found a security vulnerability in this repository, please follow our responsible disclosure process outlined in [SECURITY.md](SECURITY.md).  

## Contributing  
We welcome contributions that align with the Programme’s objectives. Please read our [Contributing](CONTRIBUTING.md) guidelines before submitting pull requests.  

## Acknowledgements  
This repository has benefited from collaboration with various organisations. For a list of acknowledgments, see [ACKNOWLEDGEMENTS.md](ACKNOWLEDGEMENTS.md).  

## Support and Contact  
For questions or support, check our Issues or contact the NDTP team on ndtp@businessandtrade.gov.uk.

**Maintained by the National Digital Twin Programme (NDTP).**  

© Crown Copyright 2026. This work has been developed by the National Digital Twin Programme and is legally attributed to the UK's Department for Business, Innovation, Science and Trade (BIST) as the governing entity.
