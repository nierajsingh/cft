/*******************************************************************************
 * Copyright (c) 2016 Pivotal Software, Inc. and others 
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
package org.eclipse.cft.server.core.internal.client;

import java.util.ArrayList;
import java.util.List;

import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.cloudfoundry.client.lib.domain.CloudSpace;
import org.eclipse.cft.server.core.internal.spaces.CloudOrgsAndSpaces;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.springframework.security.oauth2.common.OAuth2AccessToken;

/**
 * 
 * A basic <b>standalone</b> client that wraps around an actual Cloud Foundry
 * client, and allows registration, validation and fetching of cloud spaces from
 * Cloud Foundry. This client is not associated with any Cloud Foundry server
 * instance and can be discarded without affecting any Cloud Foundry server
 * instance.
 * <p/>
 * To create a client linked to a Cloud Foundry server instance that allows
 * additional cloud operations performed on modules and services in the cloud
 * server, use {@link CloudServerCFClient} instead.
 *
 */
public class CFClient {

	protected final CloudFoundryOperations v1Operations;

	public CFClient(CloudFoundryOperations v1Operations) {
		this.v1Operations = v1Operations;
	}

	public CloudFoundryOperations getV1Operations() {
		return v1Operations;
	}

	public void register(String userName, String password) {
		v1Operations.register(userName, password);
	}

	public CFAccessToken login() throws CoreException {
		OAuth2AccessToken token = v1Operations.login();
		return new V1AccessToken(token);
	}

	/**
	 * Attempts to retrieve cloud spaces using the given set of credentials and
	 * server URL. This bypasses the session client in a Cloud Foundry server
	 * instance, if one exists for the given server URL, and therefore attempts
	 * to retrieve the cloud spaces with a disposable, temporary client that
	 * logs in with the given credentials.Therefore, if fetching orgs and spaces
	 * from an existing server instance, please use
	 * {@link CloudFoundryServerBehaviour#getCloudSpaces(IProgressMonitor)}.
	 * @param client
	 * @param selfSigned true if connecting to a self-signing server. False
	 * otherwise
	 * @param monitor which performs client login checks, and basic error
	 * handling. False if spaces should be obtained directly from the client
	 * API.
	 * 
	 * @return resolved orgs and spaces for the given credential and server URL.
	 */

	public CloudOrgsAndSpaces getCloudSpaces(IProgressMonitor monitor) throws CoreException {

		List<CloudSpace> foundSpaces = getV1Spaces();
		if (foundSpaces != null && !foundSpaces.isEmpty()) {
			List<CloudSpace> actualSpaces = new ArrayList<CloudSpace>(foundSpaces);
			CloudOrgsAndSpaces orgsAndSpaces = new CloudOrgsAndSpaces(actualSpaces);
			return orgsAndSpaces;
		}

		return null;
	}

	protected List<CloudSpace> getV1Spaces() {
		return v1Operations.getSpaces();
	}
}
