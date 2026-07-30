package com.veeva.vault.custom.triggers;

import com.veeva.vault.sdk.api.data.RecordTriggerInfo;
import com.veeva.vault.sdk.api.query.Query;
import com.veeva.vault.sdk.api.query.QueryExecutionRequest;
import com.veeva.vault.sdk.api.query.QueryExecutionResponse;
import com.veeva.vault.sdk.api.query.QueryExecutionResult;
import com.veeva.vault.sdk.api.query.QueryService;
import com.veeva.vault.sdk.api.token.TokenRequest;
import com.veeva.vault.sdk.api.token.TokenService;
import com.veeva.vault.sdk.api.data.RecordEvent;
import com.veeva.vault.sdk.api.data.RecordTrigger;
import com.veeva.vault.sdk.api.data.RecordTriggerContext;
import com.veeva.vault.sdk.api.data.RecordChange;

import java.util.Iterator;

import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;

/**
 * This trigger demonstrates the Vault Java SDK QueryService. It uses the QueryService to:
 *
 *    - Query for existing 'vsdk_service_basics__c' records with the same name
 *    - If a record with the same name is found:
 *       - Set the new record's name__v to "Copy of: <name__v>".
 *       - Set the new record as related to the original queried record via the "related_to__c" object field.
 *    - If a record doesn't exist:
 *       - Insert the record with the name__v set as entered in the UI.
 *
 */

@RecordTriggerInfo(object = "vsdk_service_basics__c", events = {RecordEvent.BEFORE_INSERT})
public class vSDKQueryService implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

    	RecordEvent recordEvent = recordTriggerContext.getRecordEvent();
    	QueryService queryService = ServiceLocator.locate(QueryService.class);

    	if (recordEvent.toString().equals("BEFORE_INSERT")) {
	        for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {

	            String name = inputRecord.getNew().getValue("name__v", ValueType.STRING);
	            String relatedTo = inputRecord.getNew().getValue("related_to__c", ValueType.STRING);

	            // Break out of the trigger code if the new record has a related "vsdk_service_basics__c" record.
	            // This indicates that the records are new inserts from "vSDKRecordService.java" and do not need processing.
	            if (relatedTo != "" && relatedTo != null) {
	            	break;
	            }

	            //Set the query up. Verify these queries using the API.
	            //The record name is supplied through a TokenRequest (Vault escapes String tokens and inserts the
	            //quoted literal, so the token is NOT wrapped in quotes). The "Copy of" LIKE subquery keeps
	            //queryService.escapeValue() because its trailing '%' wildcard must stay outside the escaped value.
	            TokenService tokenService = ServiceLocator.locate(TokenService.class);
	            TokenRequest tokenRequest = tokenService.newTokenRequestBuilder()
	            		.withValue("Custom.record_name", name)
	            		.build();

	            Query query = queryService.newQueryBuilder()
	            		.withSelect(VaultCollections.asList("id", "name__v",
	            				"(select id from vsdk_service_basics__cr where name__v like '" + queryService.escapeValue("Copy of: '" + name + "'") + " %')"))
	            		.withFrom("vsdk_service_basics__c")
	            		.withWhere("name__v like ${Custom.record_name}")
	            		.build();

	            QueryExecutionRequest queryRequest = queryService.newQueryExecutionRequestBuilder()
	            		.withQuery(query)
	            		.withTokenRequest(tokenRequest)
	            		.build();

	            queryService.query(queryRequest)
	            		.onSuccess(queryResponse -> {
	            		    //QueryExecutionResponse parsed with Iterator
	            		    queryAsIterator(queryResponse, inputRecord, name);

	            		    //QueryExecutionResponse parsed with Stream
	            		    queryAsStream(queryResponse, inputRecord, name);

	            		    //If the QueryService returns no matching records, insert the new record as is.
	            		    if (queryResponse.getResultCount() == 0) {
	            		        inputRecord.getNew().setValue("name__v", name);
	            		    }
	            		})
	            		.execute();
	        }
    	}
    }


    public void queryAsIterator(QueryExecutionResponse queryResponse, RecordChange inputRecord, String recordName) {

        Iterator<QueryExecutionResult> iterator = queryResponse.streamResults().iterator();

    	//Iterate over the QueryExecutionResponse to grab each individual queried record.
    	//Once retrieved, you can access the subquery to find out how many "Copy of" records exist already.
    	//The subquery total value is used to increment the "Copy of" records when a user tries to
    	//insert multiple records with the same name.

        while (iterator.hasNext()) {

            QueryExecutionResult qr = iterator.next();
            QueryExecutionResponse subQueryResponse = qr.getSubqueryResponse("vsdk_service_basics__cr");

            //Change the name of the inserted record to "Copy of: '<name>' x".
            //Set the record as related to the queried record via the custom "related_to__c" object reference field.
            String id = qr.getValue("id", ValueType.STRING);
            inputRecord.getNew().setValue("name__v", "Copy of: '" + recordName + "' " + (subQueryResponse.getResultCount() + 1));
            inputRecord.getNew().setValue("related_to__c", id);
        }
    }

    public void queryAsStream(QueryExecutionResponse queryResponse, RecordChange inputRecord, String recordName) {

    	//Stream over the QueryExecutionResponse to grab each individual queried record.
    	//Once retrieved, you can access the subquery to find out how many "Copy of" records exist already.
    	//The subquery total value is used to increment the "Copy of" records when a user tries to
    	//insert multiple records with the same name.

    	queryResponse.streamResults().forEach(qr -> {
            QueryExecutionResponse subQueryResponse = qr.getSubqueryResponse("vsdk_service_basics__cr");

            //Change the name of the inserted record to "Copy of: '<name>' x".
            //Set the record as related to the queried record via the custom "related_to__c" object reference field.
            String id = qr.getValue("id", ValueType.STRING);
            inputRecord.getNew().setValue("name__v", "Copy of: '" + recordName + "' " + (subQueryResponse.getResultCount() + 1));
            inputRecord.getNew().setValue("related_to__c", id);

        });
    }
}
