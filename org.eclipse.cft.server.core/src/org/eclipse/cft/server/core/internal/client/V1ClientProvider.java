/*******************************************************************************
 * Copyright (c) 2017 Pivotal Software, Inc. and others 
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

import java.net.MalformedURLException;
import java.net.URL;

import org.cloudfoundry.client.lib.CloudCredentials;
import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.cloudfoundry.client.lib.HttpProxyConfiguration;
import org.cloudfoundry.client.lib.domain.CloudSpace;
import org.eclipse.cft.server.core.internal.CloudErrorUtil;
import org.eclipse.cft.server.core.internal.CloudFoundryLoginHandler;
import org.eclipse.cft.server.core.internal.CloudFoundryPlugin;
import org.eclipse.cft.server.core.internal.CloudFoundryServer;
import org.eclipse.cft.server.core.internal.CloudServerUtil;
import org.eclipse.cft.server.core.internal.CloudUtil;
import org.eclipse.cft.server.core.internal.Messages;
import org.eclipse.cft.server.core.internal.V1Requests;
import org.eclipse.cft.server.core.internal.spaces.CloudFoundrySpace;
import org.eclipse.cft.server.core.internal.spaces.CloudOrgsAndSpaces;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.wst.server.core.IServer;

@SuppressWarnings("deprecation")
public class V1ClientProvider implements CFClientProvider {

	@Override
	public boolean supports(String serverUrl, CFInfo info) {
		// support all CF as this is the default client provider
		return true;
	}

	@Override
	public CFClient createClient(IServer server, CFCloudCredentials cloudCredentials, IProgressMonitor monitor)
			throws CoreException {

		CloudFoundryServer cloudServer = CloudServerUtil.getCloudServer(server);

		String location = cloudServer.getUrl();

		String passcode = null;
		String userName = null;
		String tokenValue = null;
		String password = null;
		boolean sso = false;

		if (cloudCredentials != null) {
			passcode = cloudCredentials.getPasscode();
			userName = cloudCredentials.getUser();
			tokenValue = cloudCredentials.getAuthTokenAsJson();
			password = cloudCredentials.getPassword();
			sso = cloudCredentials.isPasscodeSet();
		}
		else {
			passcode = cloudServer.getPasscode();
			userName = cloudServer.getUsername();
			tokenValue = cloudServer.getToken();
			password = cloudServer.getPassword();
			sso = cloudServer.isSso();
		}

		CloudFoundrySpace space = cloudServer.getCloudFoundrySpace();
		boolean selfSigned = cloudServer.isSelfSigned();

		SubMonitor progress = SubMonitor.convert(monitor);

		progress.beginTask(Messages.CONNECTING, IProgressMonitor.UNKNOWN);
		try {

			CloudFoundryOperations client = createClient(cloudServer, location, space, selfSigned, userName, password,
					sso, tokenValue, passcode, monitor);

			AdditionalV1Operations additionalV1 = createAdditionalV1ClientOperations(client, cloudServer);
			ClientRequestFactory requestFactory = V1Requests.INSTANCE.getRequestFactory(cloudServer.getServer(), client,
					additionalV1);
			return new V1CFClient(client, cloudServer.getBehaviour(), requestFactory);
		}
		catch (RuntimeException t) {
			throw CloudErrorUtil.checkServerCommunicationError(t);
		}
		finally {
			progress.done();
		}
	}

	private AdditionalV1Operations createAdditionalV1ClientOperations(CloudFoundryOperations client,
			CloudFoundryServer cloudServer) throws CoreException {

		HttpProxyConfiguration httpProxyConfiguration = cloudServer.getProxyConfiguration();
		CloudSpace sessionSpace = null;
		CloudFoundrySpace storedSpace = cloudServer.getCloudFoundrySpace();

		// Fetch the session spac if it is not available from the server, as it
		// is required for the additional v1 operations
		if (storedSpace != null) {
			sessionSpace = storedSpace.getSpace();
			if (sessionSpace == null && storedSpace.getOrgName() != null && storedSpace.getSpaceName() != null) {
				CloudOrgsAndSpaces spacesFromCF = V1CFClient.getCloudSpaces(client);
				if (spacesFromCF != null) {
					sessionSpace = spacesFromCF.getSpace(storedSpace.getOrgName(), storedSpace.getSpaceName());
				}
			}
		}

		if (sessionSpace == null) {
			throw CloudErrorUtil.toCoreException("No Cloud space resolved for " + cloudServer.getServer().getId() //$NON-NLS-1$
					+ ". Please verify that the server is connected and refreshed and try again."); //$NON-NLS-1$
		}
		return new AdditionalV1Operations(client, sessionSpace, cloudServer.getBehaviour().getCloudInfo(),
				httpProxyConfiguration, cloudServer, cloudServer.isSelfSigned());
	}

	public static CloudFoundryOperations createClient(CloudFoundryServer cloudServer, String location,
			CloudFoundrySpace space, boolean selfSigned, String userName, String password, boolean sso,
			String tokenValue, String passcode, IProgressMonitor monitor) throws CoreException {
		CloudFoundryOperations client;

		if (sso) {
			CloudCredentials credentials = CloudUtil.createSsoCredentials(passcode, tokenValue);
			client = createClient(location, credentials, space, selfSigned);
		}
		else {
			client = createClient(location, new CloudCredentials(userName, password), space, selfSigned);
		}

		final CloudFoundryOperations finalClient = client;

		new ClientRequest<Void>(Messages.VALIDATING_CREDENTIALS) {

			@Override
			protected Void doRun(CloudFoundryOperations client, SubMonitor progress) throws CoreException {
				CloudFoundryLoginHandler operationsHandler = new CloudFoundryLoginHandler(client, cloudServer);
				int attempts = 5;

				operationsHandler.login(progress, attempts, CloudOperationsConstants.LOGIN_INTERVAL);
				return null;
			}

			@Override
			protected CloudFoundryOperations getClient(IProgressMonitor monitor) throws CoreException {
				return finalClient;
			}

		}.run(monitor);

		return client;
	}

	/**
	 * Creates a new client to the specified server URL using the given
	 * credentials. This does NOT connect the client to the server, nor does it
	 * set the client as the session client for the server behaviour. The
	 * session client is set indirectly via {@link #connect(IProgressMonitor)}
	 * @param serverURL server to connect to. Must NOT be null.
	 * @param credentials must not be null.
	 * @param cloudSpace optional. Can be null, as a client can be created
	 * without specifying an org/space (e.g. a client can be created for the
	 * purpose of looking up all the orgs/spaces in a server)
	 * @param selfSigned true if connecting to a server with self signed
	 * certificate. False otherwise
	 * @return non-null client.
	 * @throws CoreException if failed to create client.
	 */
	private static CloudFoundryOperations createClient(String serverURL, CloudCredentials credentials,
			CloudFoundrySpace cloudSpace, boolean selfSigned) throws CoreException {

		URL url;
		try {
			url = new URL(serverURL);
			int port = url.getPort();
			if (port == -1) {
				port = url.getDefaultPort();
			}

			// If no cloud space is specified, use appropriate client factory
			// API to create a non-space client
			// NOTE that using a space API with null org and space will result
			// in errors as that API will
			// expect valid org and space values.
			return cloudSpace != null
					? CloudFoundryPlugin.getCloudFoundryClientFactory().getCloudFoundryOperations(credentials, url,
							cloudSpace.getOrgName(), cloudSpace.getSpaceName(), selfSigned)
					: CloudFoundryPlugin.getCloudFoundryClientFactory().getCloudFoundryOperations(credentials, url,
							selfSigned);
		}
		catch (MalformedURLException e) {
			throw new CoreException(new Status(IStatus.ERROR, CloudFoundryPlugin.PLUGIN_ID,
					"The server url " + serverURL + " is invalid: " + e.getMessage(), e)); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	public static CloudOrgsAndSpaces getCloudSpaces(CloudFoundryServer cfServer, CloudCredentials credentials,
			final String url, boolean selfSigned, final boolean sso, final String passcode, String tokenValue,
			IProgressMonitor monitor) throws CoreException {
		final CloudFoundryOperations operations = CloudFoundryServerBehaviour.createExternalClientLogin(cfServer, url,
				credentials.getEmail(), credentials.getPassword(), selfSigned, sso, passcode, tokenValue, monitor);

		return new ClientRequest<CloudOrgsAndSpaces>(Messages.GETTING_ORGS_AND_SPACES) {
			@Override
			protected CloudOrgsAndSpaces doRun(CloudFoundryOperations client, SubMonitor progress)
					throws CoreException {
				return V1CFClient.getCloudSpaces(client);
			}

			@Override
			protected CloudFoundryOperations getClient(IProgressMonitor monitor) throws CoreException {
				return operations;
			}

		}.run(monitor);
	}

}
