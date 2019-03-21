package com.veeva.vault.custom.actions;

import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.custom.udc.vSDKHttpCallouts;
import com.veeva.vault.sdk.api.action.DocumentAction;
import com.veeva.vault.sdk.api.action.DocumentActionContext;
import com.veeva.vault.sdk.api.action.DocumentActionInfo;
import com.veeva.vault.sdk.api.document.*;



/******************************************************************************                                                     
 * Document Action:     External Http Callout
 * Author:      Kevin Nee @ Veeva
 * Date:        2019-03-19
 *-----------------------------------------------------------------------------
 * Description: Provides an example of how to use a External HTTP Callout.
 * 
 * 				This makes an HTTP callout to a free open source public API 
 *              endpoint (https://reqres.in/api/unknown/2). 
 *              
 *              The returned data from the endpoint is then used to set an
 *              external ID (vsdk_http_external_id__c) on the document.
 * 
 *-----------------------------------------------------------------------------
 * Copyright (c) 2019 Veeva Systems Inc.  All Rights Reserved.
 *      This code is based on pre-existing content developed and
 *      owned by Veeva Systems Inc. and may only be used in connection
 *      with the deliverable with which it was provided to Customer.
 *--------------------------------------------------------------------
 *
 *******************************************************************************/

@DocumentActionInfo(label="SDK: External Http Callout")
public class vSDKExternalHttpCallout implements DocumentAction {
	
    public void execute(DocumentActionContext documentActionContext) {

    	DocumentVersion docVersion = documentActionContext.getDocumentVersions().get(0);
    	
    	String id = docVersion.getValue("id", ValueType.STRING);
    	
    	vSDKHttpCallouts.externalHttpCallout(id);
    }

	public boolean isExecutable(DocumentActionContext documentActionContext) {
	    return true;
	}
}