package com.veeva.vault.custom.actions;

import java.util.List;
import java.util.Map;

import com.veeva.vault.custom.udc.vSDKHttpCallouts;
import com.veeva.vault.sdk.api.action.DocumentAction;
import com.veeva.vault.sdk.api.action.DocumentActionContext;
import com.veeva.vault.sdk.api.action.DocumentActionInfo;
import com.veeva.vault.sdk.api.core.LogService;
import com.veeva.vault.sdk.api.core.RequestContext;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.document.DocumentVersion;
import com.veeva.vault.sdk.api.group.GetGroupsResponse;
import com.veeva.vault.sdk.api.group.Group;
import com.veeva.vault.sdk.api.group.GroupService;
import com.veeva.vault.sdk.api.role.DocumentRole;
import com.veeva.vault.sdk.api.role.DocumentRoleService;
import com.veeva.vault.sdk.api.role.DocumentRoleUpdate;
import com.veeva.vault.sdk.api.role.GetDocumentRolesResponse;


/******************************************************************************                                                     
 * Document Action:     SDK: Local Http Callout
 * Author:      Kevin Nee @ Veeva
 * Date:        2019-03-06
 *-----------------------------------------------------------------------------
 * Description: Provides  an example of a local HTTP Callout.
 * 
 * 				The action automatically changes Owner, Approver,
 *              Senior Leader, Group Leader roles on a vSDK HTTP Document.
 *              
 *              This is done through the DocumentRoleService and a User Action 
 *              Prompt. The prompt ask for the names of users in the updated 
 *              roles.
 *              
 *              Once the new users have been provided and roles have been 
 *              updated, a local HttpService API call is made to initialize
 *              the vSDK HTTP Workflow on the document.
 *              
 *              Only users in the Vault Owners  group can see 
 *              and execute the document action.
 * 
 *-----------------------------------------------------------------------------
 * Copyright (c) 2019 Veeva Systems Inc.  All Rights Reserved.
 *      This code is based on pre-existing content developed and
 *      owned by Veeva Systems Inc. and may only be used in connection
 *      with the deliverable with which it was provided to Customer.
 *--------------------------------------------------------------------
 *
 *******************************************************************************/

@DocumentActionInfo(label="SDK: Local Http Callout", user_input_object="vsdk_user_input_object__c", user_input_object_type="")
public class vSDKLocalHttpCalloutAction implements DocumentAction {
	
    // Roles to check on the Document
    static final String OWNER = "owner__v";
    static final String APPROVER = "approver__v";
    static final String VIEWER = "viewer__v";
    static final String EDITOR = "editor__v";
    
	
	//Only show the SDK user action if the current user is a Vault Owner or in the Employee Success group
	public boolean isExecutable(DocumentActionContext context) {
		
		GroupService groupService = ServiceLocator.locate(GroupService.class);
		
		String currentUserId = RequestContext.get().getCurrentUserId();
		GetGroupsResponse groupsResponse = groupService.getGroupsByNames(VaultCollections.asList("vault_owners__v"));

		Group vaultOwner = groupsResponse.getGroupByName("vault_owners__v");
		
		boolean isCurrentUserInGroup = groupService.isUserInGroup(currentUserId, vaultOwner);
		
		return isCurrentUserInGroup;
	}
	
    public void execute(DocumentActionContext documentActionContext) {

    	RecordService recordService = ServiceLocator.locate(RecordService.class);
    	LogService logService = ServiceLocator.locate(LogService.class);
        DocumentRoleService docRoleService = ServiceLocator.locate(DocumentRoleService.class);
        
        // Assuming this is a Document use action, there is one document in the documentActionContext
        List<DocumentVersion> docVersionList = documentActionContext.getDocumentVersions();
        List<DocumentRoleUpdate> documentRoleUpdates = VaultCollections.newList();
    	
    	//New getUserInputRecord() method to retrieve the input record data.
    	Record inputRecord = documentActionContext.getUserInputRecord();
        
        Map<String,String> userToRoleMap = VaultCollections.newMap();
        Map<String,String> params = VaultCollections.newMap();
        
        userToRoleMap.put(OWNER, inputRecord.getValue("owner__c", ValueType.STRING));
        userToRoleMap.put(APPROVER, inputRecord.getValue("owner__c", ValueType.STRING));
        userToRoleMap.put(VIEWER, inputRecord.getValue("viewer__c", ValueType.STRING));
        userToRoleMap.put(EDITOR, inputRecord.getValue("editor__c", ValueType.STRING));

        checkDocumentRole(docVersionList,documentRoleUpdates,userToRoleMap);
        
        if (documentRoleUpdates.size() > 0) {
	        docRoleService.batchUpdateDocumentRoles(documentRoleUpdates)
	        .rollbackOnErrors()
	        .execute();
	        
	        logService.info("Document Role update successful.");
	        
	        //Delete temporary user input record
	        recordService.batchDeleteRecords(VaultCollections.asList(inputRecord)).rollbackOnErrors().execute();
	        
	        //Loop through all documents and initiate the APR Document workflow for the new owner (manager) of the record.
    		for (DocumentVersion docVersion : docVersionList) {
            	String version_id = docVersion.getValue("id", ValueType.STRING) + "_" + 
        				docVersion.getValue("major_version_number__v", ValueType.NUMBER).toString() + "_" + 
        				docVersion.getValue("minor_version_number__v", ValueType.NUMBER).toString();
            	
            	vSDKHttpCallouts.localGetLifecycleActions(version_id, userToRoleMap.get(OWNER), params);
    		}
        }
    }
    
    private void checkDocumentRole(List<DocumentVersion> docVersionList, List<DocumentRoleUpdate> documentRoleUpdates, Map<String,String> userToRoleMap) {
    	LogService logService = ServiceLocator.locate(LogService.class);
        DocumentRoleService docRoleService = ServiceLocator.locate(DocumentRoleService.class);
        
        // Loop through all defined user role changes in the userToRoleMap. This checks to see if the user
        // exists in the requested role already and if not, they are added.
        // All other existing users on the role are then removed.
        
        for (String roleToCheck : userToRoleMap.keySet()) {
        	String userId = userToRoleMap.get(roleToCheck);
	        GetDocumentRolesResponse docRolesResponse = docRoleService.getDocumentRoles(docVersionList, roleToCheck);
	        
	        for (DocumentVersion docVersion : docVersionList) {    
		        DocumentRole checkedRole = docRolesResponse.getDocumentRole(docVersion);
		
		        // Check if new user is in the specified role
			    if (userId != null) {
		        	boolean isUserInCheckedRole = docRoleService
			                .getUserInDocumentRoles(userId, VaultCollections.asList(checkedRole))
			                .isUserInDocumentRole(checkedRole);
			        
			        // Add new user to the specified role
			        if (!isUserInCheckedRole) {
			        	
			        	logService.info("Create DocumentRoleUpdate for userId {} on role {}.", userId, roleToCheck);
			            DocumentRoleUpdate docRoleUpdate = docRoleService.newDocumentRoleUpdate(roleToCheck, docVersion);
			            if (!roleToCheck.equals(APPROVER)) {
				            docRoleUpdate.addUsers(VaultCollections.asList(userId));
			            }
			            if (checkedRole.getUsers().size() > 0) {
			            	docRoleUpdate.removeUsers(checkedRole.getUsers());
			            }
			            documentRoleUpdates.add(docRoleUpdate);
			        }
		        }
			    else {
			    	userId = (checkedRole.getUsers().size() > 0) ? checkedRole.getUsers().get(0) : null;
			    	userToRoleMap.put(roleToCheck, userId);
			    }
	        }
        }
    }
}
