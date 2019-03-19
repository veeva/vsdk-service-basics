package com.veeva.vault.custom.triggers;

import com.veeva.vault.sdk.api.data.RecordTriggerInfo;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryResult;
import com.veeva.vault.sdk.api.query.QueryService;
import com.veeva.vault.sdk.api.data.RecordEvent;
import com.veeva.vault.sdk.api.data.RecordTrigger;
import com.veeva.vault.sdk.api.data.RecordTriggerContext;
import com.veeva.vault.sdk.api.data.RecordChange;

import java.util.Iterator;

import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;

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
	            //The QueryService.escape(string) escapes single quotes and backslashes for use within the query.
	            String query = "select id, name__v, "
	            			+ "(select id from vsdk_service_basics__cr where name__v like '" + queryService.escape("Copy of: '" + name + "'") + " %') "  
	            			+ "from vsdk_service_basics__c where name__v like '" + name + "'";
	            QueryResponse queryResponse = queryService.query(query);
	            
	            //QueryResponse parsed with Iterator
	            queryAsIterator(queryResponse, inputRecord, name);
	            
	            //QueryResponse parsed with Stream
	            queryAsStream(queryResponse, inputRecord, name);
	            
	            
	            //If the QueryService returns no matching records, insert the new record as is.
	            if (queryResponse.getResultCount() == 0) {
	                inputRecord.getNew().setValue("name__v", name);
	            }
	        }
    	}
    }
    
    
    public void queryAsIterator(QueryResponse queryResponse, RecordChange inputRecord, String recordName) {
    	
        Iterator<QueryResult> iterator = queryResponse.streamResults().iterator();
        
    	//Iterate over the QueryResponse to grab each individual queried record.
    	//Once retrieved, you can access the subquery to find out how many "Copy of" records exist already.
    	//The subquery total value is used to increment the "Copy of" records when a user tries to 
    	//insert multiple records with the same name.
        
        while (iterator.hasNext()) {
        	
            QueryResult qr = (QueryResult) iterator.next();
            QueryResponse subQueryResponse = qr.getSubqueryResponse("vsdk_service_basics__cr");
            
            //Change the name of the inserted record to "Copy of: '<name>' x".
            //Set the record as related to the queried record via the custom "related_to__c" object reference field.
            String id = qr.getValue("id", ValueType.STRING);
            inputRecord.getNew().setValue("name__v", "Copy of: '" + recordName + "' " + (subQueryResponse.getResultCount() + 1));
            inputRecord.getNew().setValue("related_to__c", id);
        }
    }
   
    public void queryAsStream(QueryResponse queryResponse, RecordChange inputRecord, String recordName) {
    	
    	//Stream  over the QueryResponse to grab each individual queried record.
    	//Once retrieved, you can access the subquery to find out how many "Copy of" records exist already.
    	//The subquery total value is used to increment the "Copy of" records when a user tries to 
    	//insert multiple records with the same name.
    	
    	queryResponse.streamResults().forEach(qr -> {
            QueryResponse subQueryResponse = qr.getSubqueryResponse("vsdk_service_basics__cr");
            
            //Change the name of the inserted record to "Copy of: '<name>' x".
            //Set the record as related to the queried record via the custom "related_to__c" object reference field.
            String id = qr.getValue("id", ValueType.STRING);
            inputRecord.getNew().setValue("name__v", "Copy of: '" + recordName + "' " + (subQueryResponse.getResultCount() + 1));
            inputRecord.getNew().setValue("related_to__c", id);
            
        });
    }
}