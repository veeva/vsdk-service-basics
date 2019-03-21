package com.veeva.vault.custom.actions;

import java.util.List;
import java.util.Map;

import com.veeva.vault.custom.udc.vSDKHttpCallouts;
import com.veeva.vault.sdk.api.action.DocumentAction;
import com.veeva.vault.sdk.api.action.DocumentActionContext;
import com.veeva.vault.sdk.api.action.DocumentActionInfo;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.document.DocumentVersion;
import com.veeva.vault.sdk.api.query.QueryResponse;
import com.veeva.vault.sdk.api.query.QueryService;


/******************************************************************************                                                     
 * Document Action:     Vault To Vault Http Callout
 * Author:      Kevin Nee @ Veeva
 * Date:        2019-03-06
 *-----------------------------------------------------------------------------
 * Description: Provides an example of how to use a Vault-to-Vault HTTP Callout.
 * 
 * 				This creates a CrossLink in the target vault via the specified 
 * 				Vault to Vault Connection. 
 * 
 *-----------------------------------------------------------------------------
 * Copyright (c) 2019 Veeva Systems Inc.  All Rights Reserved.
 *      This code is based on pre-existing content developed and
 *      owned by Veeva Systems Inc. and may only be used in connection
 *      with the deliverable with which it was provided to Customer.
 *--------------------------------------------------------------------
 *
 *******************************************************************************/

@DocumentActionInfo(label="SDK: Vault To Vault Http Callout")
public class vSDKVaultToVaultHttpCalloutAction implements DocumentAction {
	
    public void execute(DocumentActionContext documentActionContext) {

    	QueryService queryService = ServiceLocator.locate(QueryService.class);
    	DocumentVersion docVersion = documentActionContext.getDocumentVersions().get(0);
    	Map<String, String> httpParams = VaultCollections.newMap();
    	List<String> connections = VaultCollections.newList();
    	
    	String version_id = docVersion.getValue("id", ValueType.STRING) + "_" + 
    				docVersion.getValue("major_version_number__v", ValueType.NUMBER).toString() + "_" + 
    				docVersion.getValue("minor_version_number__v", ValueType.NUMBER).toString();
    	
    	httpParams.put("docId", docVersion.getValue("id", ValueType.STRING));
    	httpParams.put("docName", docVersion.getValue("name__v", ValueType.STRING));
    	httpParams.put("type", "vSDK HTTP Doctype");
    	httpParams.put("lifecycle", "vSDK HTTP Doctype Lifecycle");
    	
    	String query = "select id, (select api_name__sys, remote_connection_id__sys from document_vsdk_connection__cr) from documents where version_id = '" + version_id + "'";

    	QueryResponse queryResponse = queryService.query(query);
    	
    	queryResponse.streamResults().forEach(qr -> {
            QueryResponse subQueryResponse = qr.getSubqueryResponse("document_vsdk_connection__cr");
            
            subQueryResponse.streamResults().forEach(subqr -> {
            	String connection = subqr.getValue("api_name__sys", ValueType.STRING);
            	String remoteConnectionId = subqr.getValue("remote_connection_id__sys", ValueType.STRING);
            	vSDKHttpCallouts.v2vHttpQuery(httpParams, connection, remoteConnectionId);
            });
        });
    }

	public boolean isExecutable(DocumentActionContext documentActionContext) {
	    return true;
	}
}
