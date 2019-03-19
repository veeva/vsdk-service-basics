# Vault Java SDK Sample - vsdk-service-basics

**Please see the [project wiki](https://github.com/veeva/vsdk-service-basics/wiki) for a detailed walkthrough.**

To help with understanding Vault Java SDK Service interfaces, we created the **vsdk-service-basics** project. We will walk you through running this simple sample Trigger.

This project contains examples of:

* Query Service
* Record Service

## How to import

Import as a Maven project. This will automatically pull in the required Vault Java SDK dependencies. 

For Intellij this is done by:
- File > Open > Navigate to project folder > Select the 'pom.xml' file > Open as Project

For Eclipse this is done by:
- File > Import > Maven > Existing Maven Projects > Navigate to project folder > Select the 'pom.xml' file

## Setup

First, you need to configure your vault so the sample trigger runs smoothly. You can do this by deploying a prepackaged set of components (.vpk).

#### Deploying the vSDK Service Basics VPK Package

1.  Clone or download the sample Maven project [vSDK Service Basics project](https://github.com/veeva/vsdk-service-basics) from GitHub.
2.  Run through the [Getting Started](https://developer.veevavault.com/sdk/#Getting_Started) guide to set up your deployment environment.
3.  Log in to your vault and navigate to **Admin > Deployment > Inbound Packages** and click **Import**: 
4.  Locate and select the **\deploy-vpk\vsdk-service-basics-components\vsdk-service-basics-components.vpk** file in the project's directory on your computer.  
5.  From the **Actions** menu, select **Review & Deploy**. Vault displays a list of all components in the package.  
6.  Click **Next**.
7.  On the confirmation page, review and click **Finish**. You will receive an email when Vault completes the deployment.

## How to run

1.  Log into your Vault: [https://login.veevavault.com/](https://login.veevavault.com/)
2.  Start the [Vault Java SDK Debugger](https://developer.veevavault.com/sdk/#Debug_Setup).
3.  Go to  **Admin > Business Admin**  tab and click on  **vSDK Service Basics**  in the left navigation panel
4.  Click the  **Create**  button in the list page
5.  Enter your name in the  **Name**  field
6.  Click  **Save**  and you should see a record get created with two "Related to: <name>" records.
7.  Repeat steps 4-6 with the same **Name** value.
8.  The triggers will create a "Copy of: 'name__v'" record and relate it to the first record that you created.  
9.  Stop Running the project.

	    
## License

This code serves as an example and is not meant for production use.

Copyright 2018 Veeva Systems Inc.
 
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
 
    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
  
