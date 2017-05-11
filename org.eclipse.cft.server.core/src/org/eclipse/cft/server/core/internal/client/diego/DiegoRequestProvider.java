/*******************************************************************************
 * Copyright (c) 2015, 2017 Pivotal Software, Inc. and others
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution. 
 * 
 * The Eclipse Public License is available at 
 * 
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * and the Apache License v2.0 is available at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * You may elect to redistribute this code under either of these licenses.
 *  
 *  Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 ********************************************************************************/
package org.eclipse.cft.server.core.internal.client.diego;

import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.eclipse.cft.server.core.internal.CloudFoundryServer;
import org.eclipse.cft.server.core.internal.CloudServerUtil;
import org.eclipse.cft.server.core.internal.V1RequestProvider;
import org.eclipse.cft.server.core.internal.client.AdditionalV1Operations;
import org.eclipse.cft.server.core.internal.client.ClientRequestFactory;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.wst.server.core.IServer;

@Deprecated
public class DiegoRequestProvider extends V1RequestProvider {

	@Override
	public ClientRequestFactory createRequestFactory(IServer server, CloudFoundryOperations v1Client, AdditionalV1Operations additionalV1Operations)
			throws CoreException {
		CloudFoundryServer cloudServer = CloudServerUtil.getCloudServer(server);
		return new DiegoRequestFactory(cloudServer.getBehaviour(), v1Client, additionalV1Operations);
	}

	@Override
	public boolean supports(IServer server) throws CoreException {
		// To check if Server is "Diego", check if the server supports
		// SSH. If so, assume it is a Diego target
		// as SSH is only available in Diego or more recent Cloud Foundry
		CloudFoundryServer cloudServer = CloudServerUtil.getCloudServer(server);
		return cloudServer.getBehaviour().supportsSsh();
	}

}
