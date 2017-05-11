/*******************************************************************************
 * Copyright (c) 2015, 2017 Pivotal Software, Inc. 
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
package org.eclipse.cft.server.core.internal;

import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.eclipse.cft.server.core.internal.client.AdditionalV1Operations;
import org.eclipse.cft.server.core.internal.client.CFClient;
import org.eclipse.cft.server.core.internal.client.ClientRequestFactory;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.wst.server.core.IServer;

/**
 * Internal use only
 * <p/>
 * Contains additional support for the V1 client like a request factory that is
 * not defined in cloud server API.
 * <p/>
 * Use {@link CFClient} instead
 * @see ClientRequestFactory
 *
 */
@Deprecated
public class V1RequestProvider {

	/**
	 * True if the target definition supports the given server. False otherwise
	 * @param server
	 * @return
	 * @throws CoreException if error occurred while determining if support for
	 * the server is possible
	 */
	public boolean supports(IServer server) throws CoreException {

		// As a basic check, the server has to at the very least be a cloud
		// server to be supported
		CloudFoundryServer cloudServer = CloudServerUtil.getCloudServer(server);
		return cloudServer != null;
	}

	/**
	 * 
	 * @param server target server where requests are sent to
	 * @return Non-null request factory. All Cloud Foundry based servers use a
	 * client for Cloud requests. This always create a new instance. It is up to
	 * the caller to manage caching if necessary
	 * @throws if error occurred resolving a request factory
	 */
	public ClientRequestFactory createRequestFactory(IServer server, CloudFoundryOperations v1Client,
			AdditionalV1Operations additionalV1Operations) throws CoreException {
		CloudFoundryServer cloudServer = CloudServerUtil.getCloudServer(server);
		return new ClientRequestFactory(cloudServer.getBehaviour(), v1Client, additionalV1Operations);
	}

}
