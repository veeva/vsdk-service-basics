package com.veeva.vault.custom.udc;

import com.veeva.vault.sdk.api.core.LogService;
import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.StringUtils;
import com.veeva.vault.sdk.api.core.UserDefinedClassInfo;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.document.DocumentService;
import com.veeva.vault.sdk.api.document.DocumentVersion;
import com.veeva.vault.sdk.api.http.HttpMethod;
import com.veeva.vault.sdk.api.http.HttpRequest;
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
	 * Runs against a local API connection to initiate a document workflow
	 * The workflow is initiated for the "userId" which is the owner of the document.
	 * See https://developer.veevavault.com/api/19.1/#initiate-user-action for details.
	 * 
	 * @param versionId of the document
	 * @param userId of the document owner
	 * @param params for API input
	 * @param action starts the document workflow
    */

    public static void localStartDocWorkflow(String versionId, String userId, Map<String,String> params, String action) {
	   
    	LogService logService = ServiceLocator.locate(LogService.class);
    	HttpService httpService = ServiceLocator.locate(HttpService.class);
    	
    	String[] version_id = StringUtils.split(versionId, "_");
	   
      	//A `newLocalHttpRequest` is an Http Callout against the same vault (local) using the user that initiated the SDK code.
    	//The user must have access to the action being performed or the Vault API will return an access error.
		HttpRequest request = httpService.newLocalHttpRequest()
                .setMethod(HttpMethod.PUT)
                .appendPath("/api/v19.1/objects/documents/" + version_id[0] + "/versions/" + version_id[1] + "/" + version_id[2] + "/lifecycle_actions/" + action)
                .setBodyParam("Approver", "user:" + userId);	

		for (String key : params.keySet()) {
			request.setBodyParam(key,params.get(key));
		}
	
        httpService.send(request, HttpResponseBodyValueType.JSONDATA)
            .onSuccess(httpResponse -> {
                int responseCode = httpResponse.getHttpStatusCode();
                logService.info("RESPONSE: " + responseCode);
                logService.info("RESPONSE: " + httpResponse.getResponseBody());
                
				JsonData response = httpResponse.getResponseBody();
				
				//This API call just initiates a workflow. Log success or errors messages depending on the results of the call.
				if (response.isValidJson()) {
					String responseStatus = response.getJsonObject().getValue("responseStatus", JsonValueType.STRING);
					
					if (responseStatus.equals("SUCCESS")) {
						logService.info("Starting HTTP  Workflow for document - " + String.join("_", version_id));
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
	 * Runs against a local API connection to locate and verify the correct document workflow.
	 * This is necessary so that the proper workflow is started when this method is used.
	 * See https://developer.veevavault.com/api/19.1/#retrieve-user-actions for details
	 * 
	 * @param versionId of the document
	 * @param userId of the document owner
	 * @param params for API input
     */
    
    public static void localGetLifecycleActions(String versionId, String userId, Map<String,String> params) {
	   
    	LogService logService = ServiceLocator.locate(LogService.class);
    	HttpService httpService = ServiceLocator.locate(HttpService.class);
    	
    	String[] version_id = StringUtils.split(versionId, "_");
	   
    	
      	//A `newLocalHttpRequest` is an Http Callout against the same vault (local) using the user that initiated the SDK code.
    	//The user must have access to the action being performed or the Vault API will return an access error.
		HttpRequest request = httpService.newLocalHttpRequest()
                .setMethod(HttpMethod.GET)
                .appendPath("/api/v19.1/objects/documents/" + version_id[0] + "/versions/" + version_id[1] + "/" + version_id[2] + "/lifecycle_actions");

        httpService.send(request, HttpResponseBodyValueType.JSONDATA)
            .onSuccess(httpResponse -> {
                int responseCode = httpResponse.getHttpStatusCode();
                logService.info("RESPONSE: " + responseCode);
                logService.info("RESPONSE: " + httpResponse.getResponseBody());
                
				JsonData response = httpResponse.getResponseBody();
				
				if (response.isValidJson()) {
					String responseStatus = response.getJsonObject().getValue("responseStatus", JsonValueType.STRING);
					
					if (responseStatus.equals("SUCCESS")) {
						logService.info("Verifying Lifecycle Actions for document - " + String.join("_", version_id));
						
						JsonArray lifecycleActions = response.getJsonObject().getValue("lifecycle_actions__v", JsonValueType.ARRAY);
						
						for (int count = 0; count < lifecycleActions.getSize(); count++) {
							JsonObject action = lifecycleActions.getValue(count, JsonValueType.OBJECT);
							String actionLabel = action.getValue("label__v", JsonValueType.STRING);
							
							//If the correct action label is located, initiate `localStartDocWorkflow` to start the document workflow.
							if (actionLabel.contains("Start HTTP Workflow")) {
								logService.info("Located the workflow action '{}'", actionLabel);
								String actionName = action.getValue("name__v", JsonValueType.STRING);
								
								vSDKHttpCallouts.localStartDocWorkflow(versionId, userId, params, actionName);
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
  		HttpRequest request = httpService.newHttpRequest("external_http_callout");
  		
		//The configured connection provides the full DNS name. 
		request.setMethod(HttpMethod.GET);
		request.appendPath("/api/unknown/2");
		request.setHeader("Content-Type", "application/json");
		
		//Required is you want to send a user's SessionId to an external system.
		//This is not necessary for this example.
		//The `setResolveTokens` method resolves the ${Session.SessionId} token to the user's 
		//in-use sesssionId.
		request.setHeader("Authorization", "${Session.SessionId}");
		request.setResolveTokens(true);

  		logService.info("externalHttpCallout request built and ready to send.");
  		
  		//Send the request the external system. The response received back should be a JSON response.
  		//First, the response is parsed into a `JsonData` object
  		//From the response, the `getJsonObject()` will get the response as a parseable `JsonObject`
  		//    * Here the `getValue` method can be used to retrieve `id`, `name`, and `pantone_value` returned from the external API
  		
  		httpService.send(request, HttpResponseBodyValueType.JSONDATA)
  		.onSuccess(httpResponse -> {
  			
  			JsonData response = httpResponse.getResponseBody();
  			
			//On a success response from the external API, populate the `vsd_http_external_id__c` field in value with:
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
     * If your vault has additional required fields, they will need to be set with `setBodyParam`
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
		
		logService.info("Creating Crosslink for document {} : {} Vault to Vault connection {}", docId, docName, connection);
	   
		//Initiate an HTTP Callout against the provided Vault to Vault connection. This record must exist and be active in both vaults.
		//The various required detailed are then set from the provided input data.
		HttpRequest request = httpService.newHttpRequest(connection)
                .setMethod(HttpMethod.POST)
                .appendPath("/api/v19.1/objects/documents")
                .setBodyParam("source_document_id__v",docId)
                .setBodyParam("source_vault_id__v", vaultId)
                .setBodyParam("source_binding_rule__v", "Latest version")
                .setBodyParam("name__v",docName)
                .setBodyParam("type__v",type)
                .setBodyParam("lifecycle__v", lifecycle);

        httpService.send(request, HttpResponseBodyValueType.JSONDATA)
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
    //The Vault to Vault Connection record contain detail for each other remote vault information.
    //This step isn't strictly necessary, but the Create Document API endpoint requires the source vault ID.
    public static void v2vHttpQuery(Map<String, String> parameters, String connection, String remoteConnectionId) {
    	
    	LogService logService = ServiceLocator.locate(LogService.class);
    	
    	//This is a vault to vault Http Request to the input connection
    	HttpService httpService = ServiceLocator.locate(HttpService.class);
		HttpRequest request = httpService.newHttpRequest(connection);

		//The configured connection provides the full DNS name. 
		//For the path, you only need to append the API endpoint after the DNS.
		//The query endpoint takes a POST where the BODY is the query itself.
		request.setMethod(HttpMethod.POST);
		request.appendPath("/api/v19.1/query");
		request.setHeader("Content-Type", "application/x-www-form-urlencoded");
		String query = "select remote_vault_id__sys from connection__sys where id contains ('" + remoteConnectionId + "')";
		request.setBodyParam("q", query);
		
		//Send the request the target vault. The response received back should be a JSON response.
		//First, the response is parsed into a `JsonData` object
		//From the response, the `getJsonObject()` will get the response as a parseable `JsonObject`
		//    * Here the `getValue` method can be used to retrieve `responseStatus`, `responseDetails`, and `data`
		//The `data` element is an array of JSON data. This is parsed into a `JsonArray` object.
		//    * Each queried record is returned as an element of the array and must be parsed into a `JsonObject`. 
		//    * Individual fields can then be retrieved from each `JsonObject` that is in the `JsonArray`.
		
		httpService.send(request, HttpResponseBodyValueType.JSONDATA)
		.onSuccess(httpResponse -> {
			
			JsonData response = httpResponse.getResponseBody();
			
			if (response.isValidJson()) {
				String responseStatus = response.getJsonObject().getValue("responseStatus", JsonValueType.STRING);
				
				if (responseStatus.equals("SUCCESS")) {
					JsonArray data = response.getJsonObject().getValue("data", JsonValueType.ARRAY);
					
					logService.info("HTTP Query Request: SUCCESS");
					
					//Retrieve each record returned from the VQL query.
					//Each element of the returned `data` JsonArray is a record with it's queried fields.
					String sourceVaultId = null;
					for (int i = 0; i < data.getSize();i++) {
						JsonObject queryRecord = data.getValue(i, JsonValueType.OBJECT);
						
						sourceVaultId = queryRecord.getValue("remote_vault_id__sys", JsonValueType.STRING);
					}
					
					logService.info("HTTP Query Request: Connection located for source vault {}", sourceVaultId);
					parameters.put("vaultId", sourceVaultId);
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
