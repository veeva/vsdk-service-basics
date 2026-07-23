# Vault Java SDK Sample - vsdk-service-basics

**Please see the [project wiki](https://github.com/veeva/vsdk-service-basics/wiki) for a detailed walkthrough.**

To help with understanding Vault Java SDK Service interfaces, we created the **vsdk-service-basics** project. We will walk you through running this sample code.

This project contains examples of:

* Query Service
* Record Service
* HTTP Callouts
    * Local 
    * External
    * Vault to Vault

## How to import

Import as a Maven project. This will automatically pull in the required Vault Java SDK dependencies. 

For Intellij this is done by:
- File > Open > Navigate to project folder > Select the 'pom.xml' file > Open as Project

For Eclipse this is done by:
- File > Import > Maven > Existing Maven Projects > Navigate to project folder > Select the 'pom.xml' file

## Prerequisites

- A Veeva Vault sandbox on release **26.1** (or a compatible release).
- **Document Workflows must be enabled in the Vault.** The HTTP Callout example
  starts a document workflow, which in 26.x is an *object workflow* on the
  `envelope__sys` object. If document workflows are not enabled, the
  `vSDK HTTP Workflow` cannot be created, deployed, or started, and the
  Local HTTP Callout test will fail.
  (Legacy single-document workflows are **not** supported by the Vault Java SDK.)
- Admin access: **Admin > Deployment**, **Admin > Configuration**,
  and **Admin > Logs > Debug Log**.

### Connections (manual setup — not included in the VPKs)

Connections hold environment-specific URLs and credentials, so they are **not**
packaged in the VPKs. Create them manually under **Admin > Configuration >
Connections** before running the HTTP Callout tests.

**External connection (for the External HTTP Callout example):**
- **Connection Type:** External
- **API Name:** `external_http_callout` (must match exactly — the code references
  it by this name)
- **URL:** `https://reqres.in`

`reqres.in` requires an API key. The key is **not** hard-coded — it is stored on
the connection so it stays out of source. The Vault Java SDK can only read
**Basic Auth** or **Client Credential** authorization values via tokens (not the
API Key authorization type), so store the key as a Basic Auth password:
1. Get a free key from <https://app.reqres.in/api-keys>.
2. **Admin > Configuration > Connections > Connection Authorizations > Create**:
   set **Connection Type** = **Basic Auth**, give it a name, enter any value for
   **User Name** (e.g. `reqres`), Save, then use the record's **Actions > Set
   Password** to enter your reqres.in key as the **Password**.
3. Edit the `external_http_callout` connection and set its **Authorization** to
   that Basic Auth Connection Authorization record. Save.

The code sends the key via the `${Auth.Password}` token in the `x-api-key`
header, so no code change is needed. Without a valid key the endpoint returns
`401` and the external ID field is not populated.

**Vault-to-Vault connection (for the Vault-to-Vault HTTP Callout example):**
- A **Vault to Vault** connection to a second Vault sandbox, established and
  **Active** on both Vaults.
- Set an **Authorized Connection User** on **both** connection records (the
  remote Vault runs the inbound CrossLink create as this user).
- On the source document, select this connection in the `vSDK Connection` field.

## Deployment

Deploy the packages in the order below. Component packages must be deployed
before the code package.

### 1. Record & Query Service components

```
Admin > Deployment > Inbound Packages > Import
  > deploy-vpk/record_and_query_service/vsdk-service-basics-components.vpk
  > Review & Deploy
```

### 2. HTTP Callout components (choose the variant for your Vault type)

```
Admin > Deployment > Inbound Packages > Import
  > deploy-vpk/httpcallouts/components/<variant>_vsdk-http-sample-components.vpk
  > Review & Deploy
```

Variants: `Base`, `Clinical`, `Multichannel`, `Quaility`, `RIM`. These create the
`vSDK HTTP Doctype` document type and its fields, the `vSDK HTTP Doctype Lifecycle`
(including the `vSDK HTTP Workflow` document workflow and all Draft-state user
actions, including `Start HTTP Workflow`), and the `vSDK User Input Object` with
its page layout.

### 3. HTTP Callout code

```
Admin > Deployment > Inbound Packages > Import
  > deploy-vpk/httpcallouts/code/vsdk-http-sample-code.vpk
  > Review & Deploy
```

## License

This code serves as an example and is not meant for production use.

Copyright 2019 Veeva Systems Inc.
 
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
 
    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
  
