package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.LogService;
import com.veeva.vault.sdk.api.core.RequestContextUserType;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.StringUtils;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.document.DocumentService;
import com.veeva.vault.sdk.api.document.DocumentVersion;
import com.veeva.vault.sdk.api.http.FormHttpRequest;
import com.veeva.vault.sdk.api.http.HttpMethod;
import com.veeva.vault.sdk.api.http.HttpResponseBodyValueType;
import com.veeva.vault.sdk.api.http.HttpService;
import com.veeva.vault.sdk.api.json.JsonArray;
import com.veeva.vault.sdk.api.json.JsonData;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonValueType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/******************************************************************************
 * User-Defined Class:  HttpCallouts
 * Author:              Kevin Nee @ Veeva
 * Date:                2019-03-19
 *-----------------------------------------------------------------------------
 * Description: Provides a reusable UDC with HTTP Callouts for Vault to Vault,
 *              local, and external use cases.
 *
 *-----------------------------------------------------------------------------
 * Copyright (c) 2019 Veeva Systems Inc.  All Rights Reserved.
 *      This code is based on pre-existing content developed and
 *      owned by Veeva Systems Inc. and may only be used in connection
 *      with the deliverable with which it was provided to Customer.
 *--------------------------------------------------------------------
 *
 *******************************************************************************/

@UserDefinedClassInfo()
public class vSDKHttpCallouts {

	/**
	 * Runs against a local API connection to initiate a document workflow.
	 * The workflow is initiated for the "userId" which is added as the Approver.
	 *
	 * Modern document workflows are object workflows on envelope__sys and are
	 * initiated with POST /objects/documents/actions/{workflow_name}. This
	 * replaces the legacy single-document lifecycle_actions endpoint, which does
	 * not support object/document workflows.
	 * See https://developer.veevavault.com/api/26.1/#initiate-document-workflow.
	 *
	 * @param versionId of the document, in the form {id}_{major}_{minor}
	 * @param userId of the user to add to the Approver participant control
	 * @param params for additional API body input
	 * @param workflowName the document workflow name (e.g. Objectworkflow.vsdk_http_workflow__c)
    */

    public static void localStartDocWorkflow(String versionId, String userId, Map<String,String> params, String workflowName) {

    	LogService logService = ServiceLocator.locate(LogService.class);
    	HttpService httpService = ServiceLocator.locate(HttpService.class);

    	String[] version_id = StringUtils.split(versionId, "_");

      	//A local Http Callout is against the same vault (local) using the user that initiated the SDK code.
    	//The user must have access to the action being performed or the Vault API will return an access error.
    	FormHttpRequest.Builder requestBuilder = httpService.newHttpRequestBuilder()
                .withLocalConnection(RequestContextUserType.INITIATING_USER)
                .withMethod(HttpMethod.POST)
                .withPath("/api/v26.1/objects/documents/actions/" + workflowName)
                // contents__sys is the document version(s) to include in the envelope.
                .withBodyParam("contents__sys", "DocumentVersion:" + versionId)
                // description__sys is a required parameter for initiating a document workflow.
                .withBodyParam("description__sys", "vSDK HTTP Workflow")
                // part_approver__c is the "Approver" participant control defined on the
                // workflow's Start step. Retrieve participant control names via the
                // "Retrieve Document Workflow Details" endpoint if the workflow changes.
                .withBodyParam("part_approver__c", "user:" + userId);

		for (String key : params.keySet()) {
			requestBuilder.withBodyParam(key, params.get(key));
		}
		FormHttpRequest request = requestBuilder.build();

        httpService.sendRequest(request, HttpResponseBodyValueType.JSONDATA)
            .onSuccess(httpResponse -> {
                int responseCode = httpResponse.getHttpStatusCode();
                logService.info("RESPONSE: " + responseCode);
                logService.info("RESPONSE: " + httpResponse.getResponseBody());

				JsonData response = httpResponse.getResponseBody();

				//This API call just initiates a workflow. Log success or error messages depending on the results of the call.
				if (response.isValidJson()) {
					String responseStatus = response.getJsonObject().getValue("responseStatus", JsonValueType.STRING);

					if (responseStatus.equals("SUCCESS")) {
						logService.info("Starting HTTP Workflow for document - " + String.join("_", version_id));
						if (response.getJsonObject().contains("data")) {
							JsonObject data = response.getJsonObject().getValue("data", JsonValueType.OBJECT);
							if (data.contains("workflow_id")) {
								logService.info("Started document workflow id {}", data.getValue("workflow_id", JsonValueType.STRING));
							}
						}
					}
					else {
						logService.info("Failed to start HTTP Workflow for document - {} ", String.join("_", version_id));
						if (response.getJsonObject().contains("responseMessage") == true) {
							String responseMessage = response.getJsonObject().getValue("responseMessage", JsonValueType.STRING);
							logService.error("ERROR: {}", responseMessage);
		        	        throw new RollbackException("OPERATION_NOT_ALLOWED", "HttpService Error on HTTP Workflow: " + responseMessage);
						}
						if (response.getJsonObject().contains("errors") ==  true) {
							JsonArray errors = response.getJsonObject().getValue("errors", JsonValueType.ARRAY);
							String type = errors.getValue(0, JsonValueType.OBJECT).getValue("type", JsonValueType.STRING);
							String message = errors.getValue(0, JsonValueType.OBJECT).getValue("message", JsonValueType.STRING);
							logService.error("ERROR {}: {}", type, message);
		        	        throw new RollbackException("OPERATION_NOT_ALLOWED", "HttpService Error on HTTP Workflow: " + message);
						}
					}
				}
            })
            .onError(httpOperationError -> {
                int responseCode = httpOperationError.getHttpResponse().getHttpStatusCode();
                logService.info("RESPONSE: " + responseCode);
                logService.info(httpOperationError.getMessage());
                logService.info(httpOperationError.getHttpResponse().getResponseBody());
            })
            .execute();
    }

	/**
	 * Runs against a local API connection to locate the correct document workflow.
	 * This is necessary so that the proper workflow is started when this method is used.
	 *
	 * Uses "Retrieve All Document Workflows" (GET /objects/documents/actions), which
	 * returns modern document workflows the user can initiate. The legacy per-version
	 * lifecycle_actions endpoint does NOT return object/document workflows.
	 * See https://developer.veevavault.com/api/26.1/#retrieve-all-document-workflows.
	 *
	 * @param versionId of the document, in the form {id}_{major}_{minor}
	 * @param userId of the user to add to the Approver participant control
	 * @param params for additional API body input
     */

    public static void localGetLifecycleActions(String versionId, String userId, Map<String,String> params) {

    	LogService logService = ServiceLocator.locate(LogService.class);
    	HttpService httpService = ServiceLocator.locate(HttpService.class);

    	String[] version_id = StringUtils.split(versionId, "_");

      	//A local Http Callout is against the same vault (local) using the user that initiated the SDK code.
    	//The user must have access to the action being performed or the Vault API will return an access error.
    	FormHttpRequest request = httpService.newHttpRequestBuilder()
                .withLocalConnection(RequestContextUserType.INITIATING_USER)
                .withMethod(HttpMethod.GET)
                .withPath("/api/v26.1/objects/documents/actions")
                .build();

        httpService.sendRequest(request, HttpResponseBodyValueType.JSONDATA)
            .onSuccess(httpResponse -> {
                int responseCode = httpResponse.getHttpStatusCode();
                logService.info("RESPONSE: " + responseCode);
                logService.info("RESPONSE: " + httpResponse.getResponseBody());

				JsonData response = httpResponse.getResponseBody();

				if (response.isValidJson()) {
					String responseStatus = response.getJsonObject().getValue("responseStatus", JsonValueType.STRING);

					if (responseStatus.equals("SUCCESS")) {
						logService.info("Verifying Lifecycle Actions for document - " + String.join("_", version_id));

						JsonArray documentWorkflows = response.getJsonObject().getValue("data", JsonValueType.ARRAY);

						for (int count = 0; count < documentWorkflows.getSize(); count++) {
							JsonObject workflow = documentWorkflows.getValue(count, JsonValueType.OBJECT);
							String workflowName = workflow.getValue("name", JsonValueType.STRING);

							//If the vSDK HTTP Workflow is located, initiate `localStartDocWorkflow` to start it.
							//The workflow only appears here when the "Start HTTP Workflow" user action makes it
							//available on the document's current lifecycle state.
							if (workflowName.contains("vsdk_http_workflow")) {
								logService.info("Located the document workflow '{}'", workflowName);
								vSDKHttpCallouts.localStartDocWorkflow(versionId, userId, params, workflowName);
							}
						}
					}
					else {
						logService.info("Failed to verify Lifecycle Actions for document - {} ", String.join("_", version_id));
						if (response.getJsonObject().contains("responseMessage") == true) {
							String responseMessage = response.getJsonObject().getValue("responseMessage", JsonValueType.STRING);
							logService.error("ERROR: {}", responseMessage);
		        	        throw new RollbackException("OPERATION_NOT_ALLOWED", "HttpService Error on HTTP Workflow: " + responseMessage);
						}
						if (response.getJsonObject().contains("errors") ==  true) {
							JsonArray errors = response.getJsonObject().getValue("errors", JsonValueType.ARRAY);
							String type = errors.getValue(0, JsonValueType.OBJECT).getValue("type", JsonValueType.STRING);
							String message = errors.getValue(0, JsonValueType.OBJECT).getValue("message", JsonValueType.STRING);
							logService.error("ERROR {}: {}", type, message);
		        	        throw new RollbackException("OPERATION_NOT_ALLOWED", "HttpService Error on HTTP Workflow: " + message);
						}
					}
				}
            })
            .onError(httpOperationError -> {
                int responseCode = httpOperationError.getHttpResponse().getHttpStatusCode();
                logService.info("RESPONSE: " + responseCode);
                logService.info(httpOperationError.getMessage());
                logService.info(httpOperationError.getHttpResponse().getResponseBody());
            })
            .execute();
    }


    /**
     * Execute an API call to an external system via the `external_http_callout` connection.
     * This is against a free open source public API endpoint (https://reqres.in/api/unknown/2)
     * The API should return an external ID that is populated on the document (docId).
     *
     * @param docId of the affected document
     *
     */

    public static void externalHttpCallout(String docId) {

      	LogService logService = ServiceLocator.locate(LogService.class);
      	DocumentService documentService = ServiceLocator.locate((DocumentService.class));
  		logService.info("Entered externalHttpCallout method");

      	//This is an external Http Request to the `external_http_callout`
  		//Vault must have a `Connection` with the API name of `external_http_callout`
  		//with a URL value of `https://reqres.in/api/unknown/2`.
      	HttpService httpService = ServiceLocator.locate(HttpService.class);

		//The configured connection provides the full DNS name.
		//reqres.in requires an API key. Rather than hard-code it, store the key on
		//the connection: add a "Basic Auth" Connection Authorization to the
		//`external_http_callout` connection with any username and the reqres.in key
		//as the PASSWORD. The ${Auth.Password} token resolves to that value at
		//runtime (with `withResolveTokens(true)`), and reqres.in expects it in the
		//`x-api-key` header. (The SDK exposes only Basic Auth / Client Credential
		//authorization values via tokens, not the API Key authorization type.)
  		FormHttpRequest request = httpService.newHttpRequestBuilder()
  				.withConnectionName("external_http_callout")
  				.withMethod(HttpMethod.GET)
  				.withPath("/api/unknown/2")
  				.withHeader("Content-Type", "application/json")
  				.withHeader("x-api-key", "${Auth.Password}")
  				.withResolveTokens(true)
  				.build();

  		logService.info("externalHttpCallout request built and ready to send.");

  		//Send the request to the external system. The response received back should be a JSON response.
  		//First, the response is parsed into a `JsonData` object.
  		//From the response, the `getJsonObject()` will get the response as a parseable `JsonObject`.
  		//    * Here the `getValue` method can be used to retrieve `id`, `name`, and `pantone_value` returned from the external API.

  		httpService.sendRequest(request, HttpResponseBodyValueType.JSONDATA)
  		.onSuccess(httpResponse -> {

  			JsonData response = httpResponse.getResponseBody();

			//On a success response from the external API, populate the `vsdk_http_external_id__c` field with:
			//`id`, `name`, and `pantone_value`
  			if (response.isValidJson()) {
  				if (httpResponse.getHttpStatusCode() == 200) {
  					JsonObject data = response.getJsonObject().getValue("data", JsonValueType.OBJECT);

  					String externalId = data.getValue("id",JsonValueType.NUMBER).toString() + "_"
  									  + data.getValue("name",JsonValueType.STRING) + "_"
  							          + data.getValue("pantone_value",JsonValueType.STRING);

  					logService.info("External HTTP Request: SUCCESS");
  					logService.info("External HTTP Data: " + externalId);

  					DocumentVersion docVersion = documentService.newDocumentWithId(docId);
  					docVersion.setValue("vsdk_http_external_id__c", externalId);
  					documentService.saveDocumentVersions(VaultCollections.asList(docVersion));
  				}
  				else {
  					logService.info("Http Callout Failed.");
  				}
  				response = null;
  			}
  			else {
  				logService.info("externalHttpCallout error: Received a non-JSON response.");
  			}
  		})
  		.onError(httpOperationError -> {
  			logService.info(httpOperationError.getMessage());
  			logService.info(httpOperationError.getHttpResponse().getResponseBody());
  		}).execute();

  		request = null;
    }


    /**
     * Opens a v2v connection to a target vault to create a crosslink document.
     *
     * **** NOTE ****
     * If your vault has additional required fields, they will need to be set with `withBodyParam`
     *
     * @param parameters for the API body
     * @param connection that vault is executing the API call against
     */

    public static void v2vCreateCrosslink(Map<String, String> parameters, String connection) {

    	LogService logService = ServiceLocator.locate(LogService.class);
    	HttpService httpService = ServiceLocator.locate(HttpService.class);

		String docId = parameters.get("docId");
		String docName = parameters.get("docName");
		String type = parameters.get("type");
		String lifecycle =  parameters.get("lifecycle");
		String vaultId =  parameters.get("vaultId");
		String remoteConnectionId = parameters.get("remoteConnectionId");

		logService.info("Creating Crosslink for document {} : {} Vault to Vault connection {}", docId, docName, connection);

		//Initiate an HTTP Callout against the provided Vault to Vault connection. This record must exist and be active in both vaults.
		//The various required details are then set from the provided input data.
		FormHttpRequest request = httpService.newHttpRequestBuilder()
                .withConnectionName(connection)
                .withMethod(HttpMethod.POST)
                .withPath("/api/v19.1/objects/documents")
                .withBodyParam("source_document_id__v", docId)
                .withBodyParam("source_vault_id__v", vaultId)
                .withBodyParam("source_binding_rule__v", "Latest version")
                .withBodyParam("name__v", docName)
                .withBodyParam("type__v", type)
                .withBodyParam("lifecycle__v", lifecycle)
                // vsdk_connection__c is a required ObjectReference field on the vSDK HTTP
                // Doctype. Set it to the target vault's V2V connection record so the
                // CrossLink create satisfies the requirement.
                .withBodyParam("vsdk_connection__c", remoteConnectionId)
                .build();

        httpService.sendRequest(request, HttpResponseBodyValueType.JSONDATA)
            .onSuccess(httpResponse -> {
                int responseCode = httpResponse.getHttpStatusCode();
                logService.info("RESPONSE: " + responseCode);
                logService.info("RESPONSE: " + httpResponse.getResponseBody());

                JsonData response = httpResponse.getResponseBody();

                if (response.isValidJson()) {
                    String responseStatus = response.getJsonObject().getValue("responseStatus", JsonValueType.STRING);

    				if (responseStatus.equals("SUCCESS")) {
    					logService.info("Successfully created CrossLink for Document ID {}", docId);
    				}
					else {
						logService.info("Failed to create CrossLink for Document ID {}", docId);
						if (response.getJsonObject().contains("responseMessage") == true) {
							String responseMessage = response.getJsonObject().getValue("responseMessage", JsonValueType.STRING);
							logService.error("FAILURE: {}", responseMessage);
		        	        throw new RollbackException("OPERATION_NOT_ALLOWED", "Failed to create CrossLink: " + responseMessage);
						}
						if (response.getJsonObject().contains("errors") ==  true) {
							JsonArray errors = response.getJsonObject().getValue("errors", JsonValueType.ARRAY);
							String errorType = errors.getValue(0, JsonValueType.OBJECT).getValue("type", JsonValueType.STRING);
							String message = errors.getValue(0, JsonValueType.OBJECT).getValue("message", JsonValueType.STRING);
							logService.error("FAILURE {}: {}", errorType, message);
		        	        throw new RollbackException("OPERATION_NOT_ALLOWED", "Create Errors: " + message);
						}
					}
                }
            })
            .onError(httpOperationError -> {
                int responseCode = httpOperationError.getHttpResponse().getHttpStatusCode();
                logService.info("RESPONSE: " + responseCode);
                logService.info(httpOperationError.getMessage());
                logService.info(httpOperationError.getHttpResponse().getResponseBody());
                throw new RollbackException("OPERATION_NOT_ALLOWED", "HttpService Error on Create CrossLink: " + httpOperationError.getMessage());
            })
            .execute();
    }


    //Retrieve the source vault ID from the target's Connection record.
    //The Vault to Vault Connection record contains details for each remote vault.
    //This step isn't strictly necessary, but the Create Document API endpoint requires the source vault ID.
    public static void v2vHttpQuery(Map<String, String> parameters, String connection, String remoteConnectionId) {

    	LogService logService = ServiceLocator.locate(LogService.class);

    	//This is a vault to vault Http Request to the input connection.
    	HttpService httpService = ServiceLocator.locate(HttpService.class);

		//The configured connection provides the full DNS name.
		//For the path, you only need to append the API endpoint after the DNS.
		//The query endpoint takes a POST where the BODY is the query itself.
		String query = "select remote_vault_id__sys from connection__sys where id contains ('" + remoteConnectionId + "')";
		FormHttpRequest request = httpService.newHttpRequestBuilder()
				.withConnectionName(connection)
				.withMethod(HttpMethod.POST)
				.withPath("/api/v19.1/query")
				.withHeader("Content-Type", "application/x-www-form-urlencoded")
				.withBodyParam("q", query)
				.build();

		//Send the request to the target vault. The response received back should be a JSON response.
		//First, the response is parsed into a `JsonData` object.
		//From the response, the `getJsonObject()` will get the response as a parseable `JsonObject`.
		//    * Here the `getValue` method can be used to retrieve `responseStatus`, `responseDetails`, and `data`.
		//The `data` element is an array of JSON data. This is parsed into a `JsonArray` object.
		//    * Each queried record is returned as an element of the array and must be parsed into a `JsonObject`.
		//    * Individual fields can then be retrieved from each `JsonObject` that is in the `JsonArray`.

		httpService.sendRequest(request, HttpResponseBodyValueType.JSONDATA)
		.onSuccess(httpResponse -> {

			JsonData response = httpResponse.getResponseBody();

			if (response.isValidJson()) {
				String responseStatus = response.getJsonObject().getValue("responseStatus", JsonValueType.STRING);

				if (responseStatus.equals("SUCCESS")) {
					JsonArray data = response.getJsonObject().getValue("data", JsonValueType.ARRAY);

					logService.info("HTTP Query Request: SUCCESS");

					//Retrieve each record returned from the VQL query.
					//Each element of the returned `data` JsonArray is a record with its queried fields.
					String sourceVaultId = null;
					for (int i = 0; i < data.getSize();i++) {
						JsonObject queryRecord = data.getValue(i, JsonValueType.OBJECT);

						sourceVaultId = queryRecord.getValue("remote_vault_id__sys", JsonValueType.STRING);
					}

					logService.info("HTTP Query Request: Connection located for source vault {}", sourceVaultId);
					parameters.put("vaultId", sourceVaultId);
					// The target vault's V2V connection record id, used to populate the
					// required vsdk_connection__c field on the CrossLink.
					parameters.put("remoteConnectionId", remoteConnectionId);
					vSDKHttpCallouts.v2vCreateCrosslink(parameters, connection);
				}
				else {
					logService.info("Failed to Query Remote Vault Connection {}", connection);
					if (response.getJsonObject().contains("responseMessage") == true) {
						String responseMessage = response.getJsonObject().getValue("responseMessage", JsonValueType.STRING);
						logService.error("ERROR: {}", responseMessage);
	        	        throw new RollbackException("OPERATION_NOT_ALLOWED", "Failed to query remote vault: " + responseMessage);
					}
					if (response.getJsonObject().contains("errors") ==  true) {
						JsonArray errors = response.getJsonObject().getValue("errors", JsonValueType.ARRAY);
						String errorType = errors.getValue(0, JsonValueType.OBJECT).getValue("type", JsonValueType.STRING);
						String message = errors.getValue(0, JsonValueType.OBJECT).getValue("message", JsonValueType.STRING);
						logService.error("ERROR {}: {}", errorType, message);
	        	        throw new RollbackException("OPERATION_NOT_ALLOWED", "Query Errors: " + message);
					}
				}
			}
			else {
				logService.info("v2vHttpUpdate error: Received a non-JSON response.");
			}
		})
		.onError(httpOperationError -> {
			  int responseCode = httpOperationError.getHttpResponse().getHttpStatusCode();
              logService.info("RESPONSE: " + responseCode);
              logService.info(httpOperationError.getMessage());
              logService.info(httpOperationError.getHttpResponse().getResponseBody());
              throw new RollbackException("OPERATION_NOT_ALLOWED", "HttpService Error Vault to Vault Query: " + httpOperationError.getMessage());
		}).execute();
    }

}
