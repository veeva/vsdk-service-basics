package com.veeva.vault.custom.triggers;

import com.veeva.vault.sdk.api.data.RecordTriggerInfo;
import com.veeva.vault.sdk.api.data.RecordEvent;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.data.RecordTrigger;
import com.veeva.vault.sdk.api.data.RecordTriggerContext;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordChange;

import java.util.List;

import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;

/**
 * This trigger demonstrates the Vault Java SDK RecordService. It uses the RecordService to:
 * 
 *    - Create two related "vsdk_service_basics__c" records with the inserted record's ID.
 *    - If is not empty, don't create any new records. This indicates that one of the new related records is passing through the trigger.
 *    - If is not empty, don't create any new records.
 *    - The related records will be named "Related to: '<name__v>' 1" and "Related to: '<name__v>' 2"
 *
 */

@RecordTriggerInfo(object = "vsdk_service_basics__c", events = {RecordEvent.AFTER_INSERT})
public class vSDKRecordService implements RecordTrigger {

    public void execute(RecordTriggerContext recordTriggerContext) {

    	RecordEvent recordEvent = recordTriggerContext.getRecordEvent();
    	RecordService recordService = ServiceLocator.locate(RecordService.class);
    	List<Record> recordList =  VaultCollections.newList();
    	
    	if (recordEvent.toString().equals("AFTER_INSERT")) {
	        for (RecordChange inputRecord : recordTriggerContext.getRecordChanges()) {
	
	            String name = inputRecord.getNew().getValue("name__v", ValueType.STRING);
	            String id = inputRecord.getNew().getValue("id", ValueType.STRING);
	            String relatedTo = inputRecord.getNew().getValue("related_to__c", ValueType.STRING);
	       
	        	// Break out of the trigger code if the new record has a related "vsdk_service_basics__c" record.
	            // This indicates that the records are "Copy of" records from "vSDKQueryService.java" 
	            // and do not need processing.
	            if (relatedTo == "" || relatedTo == null) {
	            	
	            	//Creates two related records by creating a new record via the RecordService.
	            	// The name of records is set as "Related to: <name> x"
	            	// The relation to the parent to then set with the "related_to__c" object reference field.
	            	for (int i = 1; i <= 2; i++) {
	            		
		            	 Record r = recordService.newRecord("vsdk_service_basics__c");
		                 r.setValue("name__v", "Related to: '" + name + "' " + i);
		                 r.setValue("related_to__c", id);
		                 
		                 recordList.add(r);
	            	}
	            }  
	        }
	        
	        //If there are records to insert, the batchSaveRecords takes a List<Record> as input.
	        //This list should contain every new record that you are adding or updating.
	    	if (recordList.size() > 0) {
		        recordService.batchSaveRecords(recordList)
	                .onErrors(batchOperationErrors -> {
	                
	                  //Iterate over the caught errors. 
	                  //The BatchOperation.onErrors() returns a list of BatchOperationErrors. 
	                  //The list can then be traversed to retrieve a single BatchOperationError and 
	                  //then extract an **ErrorResult** with BatchOperationError.getError(). 
	          	      batchOperationErrors.stream().findFirst().ifPresent(error -> {
	        	          String errMsg = error.getError().getMessage();
	        	          int errPosition = error.getInputPosition();
	        	          String name = recordList.get(errPosition).getValue("name__v", ValueType.STRING);
	        	          throw new RollbackException("OPERATION_NOT_ALLOWED", "Unable to create '" + recordList.get(errPosition).getObjectName() + "' record: '" +
	        	                  name + "' because of '" + errMsg + "'.");
	        	      });
	                })
	            .execute();
	    	}
    	}
    }
}