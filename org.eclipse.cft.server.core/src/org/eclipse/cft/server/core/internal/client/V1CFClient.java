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

import java.util.ArrayList;
import java.util.List;

import org.cloudfoundry.client.lib.ApplicationLogListener;
import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.cloudfoundry.client.lib.StreamingLogToken;
import org.cloudfoundry.client.lib.domain.ApplicationLog;
import org.cloudfoundry.client.lib.domain.ApplicationStats;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.client.lib.domain.CloudDomain;
import org.cloudfoundry.client.lib.domain.CloudRoute;
import org.cloudfoundry.client.lib.domain.CloudSpace;
import org.cloudfoundry.client.lib.domain.InstancesInfo;
import org.eclipse.cft.server.core.CFServiceInstance;
import org.eclipse.cft.server.core.CFServiceOffering;
import org.eclipse.cft.server.core.EnvironmentVariable;
import org.eclipse.cft.server.core.internal.CloudErrorUtil;
import org.eclipse.cft.server.core.internal.CloudFoundryPlugin;
import org.eclipse.cft.server.core.internal.CloudUtil;
import org.eclipse.cft.server.core.internal.Messages;
import org.eclipse.cft.server.core.internal.log.AppLogUtil;
import org.eclipse.cft.server.core.internal.log.CFApplicationLogListener;
import org.eclipse.cft.server.core.internal.log.CFStreamingLogToken;
import org.eclipse.cft.server.core.internal.log.CloudLog;
import org.eclipse.cft.server.core.internal.log.V1CFApplicationLogListener;
import org.eclipse.cft.server.core.internal.log.V1StreamingLogToken;
import org.eclipse.cft.server.core.internal.spaces.CloudOrgsAndSpaces;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.osgi.util.NLS;
import org.springframework.security.oauth2.common.OAuth2AccessToken;

/**
 * Wrapper around the actual v1 client that performs v1 operations for the CFT
 * behaviour. See {@link CloudFoundryServerBehaviour}
 *
 */
@SuppressWarnings("deprecation")
public class V1CFClient implements CFClient {

	private final CloudFoundryOperations v1Client;

	private final CloudFoundryServerBehaviour behaviour;

	private final ClientRequestFactory requestFactory;

	public V1CFClient(CloudFoundryOperations v1Client, CloudFoundryServerBehaviour behaviour,
			ClientRequestFactory requestFactory) {
		this.v1Client = v1Client;
		this.behaviour = behaviour;
		this.requestFactory = requestFactory;

	}

	@Override
	public String login(IProgressMonitor monitor) throws CoreException {
		try {
			// Make sure to save the token if the v1 client did a login
			OAuth2AccessToken oauth = v1Client.login();
			if (oauth != null) {
				String asJson = CloudUtil.getTokenAsJson(oauth);
				return asJson;
			}
		}
		catch (Throwable e) {
			throw CloudErrorUtil.toCoreException(e);
		}
		throw CloudErrorUtil.toCoreException(
				"Failed to login using v1 client. No OAuth2AccessToken resolved. Check if credentials or passcode are valid."); //$NON-NLS-1$
	}

	@Override
	public CFStreamingLogToken streamLogs(String appName, CFApplicationLogListener listener, IProgressMonitor monitor)
			throws CoreException {
		// Otherwise delegate to old V1 application log stream
		StreamingLogToken logToken = streamLogsV1(appName, new V1CFApplicationLogListener(listener), monitor);
		if (logToken != null) {
			return new V1StreamingLogToken(logToken);
		}
		return null;
	}

	@Override
	public List<CloudLog> getRecentLogs(String appName, IProgressMonitor monitor) throws CoreException {
		List<ApplicationLog> v1Logs = run(requestFactory.getRecentApplicationLogs(appName), monitor);
		List<CloudLog> logs = new ArrayList<>();
		for (ApplicationLog log : v1Logs) {
			logs.add(AppLogUtil.getLogFromV1(log));
		}
		return logs;
	}

	/**
	 * Attempt to reserve a route; returns true if the route could be reserved,
	 * or false otherwise. Note: This will return false if user already owns the
	 * route, or if the route is owned by another user. Will return early if
	 * cancelled, with an OperationCanceledException.
	 */
	@Override
	public boolean reserveRouteIfAvailable(final String host, final String domainName, IProgressMonitor monitor)
			throws CoreException {

		BaseClientRequest<Boolean> request = requestFactory.reserveRouteIfAvailable(host, domainName);

		CancellableRequestThread<Boolean> t = new CancellableRequestThread<Boolean>(request, monitor);
		Boolean result = t.runAndWaitForCompleteOrCancelled();

		if (result != null) {
			return result;
		}

		return false;
	}

	@Override
	public List<CFServiceInstance> deleteServices(List<String> services, IProgressMonitor monitor)
			throws CoreException {
		return run(requestFactory.getDeleteServicesRequest(services), monitor);
	}

	@Override
	public List<CFServiceInstance> createServices(CFServiceInstance[] services, IProgressMonitor monitor)
			throws CoreException {
		return run(requestFactory.getCreateServicesRequest(services), monitor);
	}

	@Override
	public void updateEnvironmentVariables(String appName, List<EnvironmentVariable> variables,
			IProgressMonitor monitor) throws CoreException {
		run(requestFactory.getUpdateEnvVarRequest(appName, variables), monitor);
	}

	@Override
	public void updateServiceBindings(String appName, List<String> services, IProgressMonitor monitor)
			throws CoreException {
		run(requestFactory.getUpdateServicesRequest(appName, services), monitor);
	}

	@Override
	public void updateAppRoutes(String appName, List<String> urls, IProgressMonitor monitor) throws CoreException {
		run(requestFactory.getUpdateAppUrlsRequest(appName, urls), monitor);
	}

	@Override
	public void updateApplicationEnableSsh(CloudFoundryApplicationModule appModule, boolean enableSsh,
			IProgressMonitor monitor) throws CoreException {
		run(requestFactory.updateApplicationEnableSsh(appModule, enableSsh), monitor);
	}

	@Override
	public void updateApplicationDiego(CloudFoundryApplicationModule appModule, boolean diego, IProgressMonitor monitor)
			throws CoreException {
		run(requestFactory.updateApplicationDiego(appModule, diego), monitor);
	}

	@Override
	public void updateApplicationMemory(CloudFoundryApplicationModule appModule, int memory, IProgressMonitor monitor)
			throws CoreException {
		run(requestFactory.getUpdateApplicationMemoryRequest(appModule, memory), monitor);
	}

	@Override
	public void stopApplication(String message, CloudFoundryApplicationModule cloudModule, IProgressMonitor monitor)
			throws CoreException {
		run(requestFactory.stopApplication(message, cloudModule), monitor);
	}

	@Override
	public CFStartingInfo restartApplication(String appName, String opLabel, IProgressMonitor monitor)
			throws CoreException {
		return CFTypesFromV1.from(run(requestFactory.restartApplication(appName, opLabel), monitor));
	}

	@Override
	public void register(String email, String password, IProgressMonitor monitor) throws CoreException {
		run(requestFactory.register(email, password), monitor);
	}

	@Override
	public void updatePassword(String newPassword, IProgressMonitor monitor) throws CoreException {
		run(requestFactory.updatePassword(newPassword), monitor);
	}

	@Override
	public void updateApplicationInstances(String appName, int instanceCount, IProgressMonitor monitor)
			throws CoreException {
		run(requestFactory.updateApplicationInstances(appName, instanceCount), monitor);
	}

	@Override
	public List<CFServiceInstance> getServices(IProgressMonitor monitor) throws CoreException {
		return run(requestFactory.getServices(), monitor);
	}

	@Override
	public List<CFServiceOffering> getServiceOfferings(IProgressMonitor monitor) throws CoreException {
		return run(requestFactory.getServiceOfferings(), monitor);
	}

	@Override
	public void deleteApplication(String appName, IProgressMonitor monitor) throws CoreException {
		run(requestFactory.deleteApplication(appName), monitor);
	}

	@Override
	public List<CFCloudDomain> getDomainsForSpace(IProgressMonitor monitor) throws CoreException {
		return CFTypesFromV1.from(run(requestFactory.getDomainsForSpace(), monitor));
	}

	@Override
	public List<CFCloudDomain> getDomainsForOrgs(IProgressMonitor monitor) throws CoreException {
		return CFTypesFromV1.from(run(requestFactory.getDomainsFromOrgs(), monitor));
	}

	@Override
	public List<String> getBuildpacks(IProgressMonitor monitor) throws CoreException {
		return run(requestFactory.getBuildpacks(), monitor);
	}

	@Override
	public void deleteRoute(String host, String domainName, IProgressMonitor monitor) throws CoreException {
		run(requestFactory.deleteRoute(host, domainName), monitor);
	}

	/*
	 * 
	 * V1-specific methods
	 * 
	 * 
	 * 
	 */

	public CloudFoundryOperations getClient() {
		return v1Client;
	}

	public StreamingLogToken streamLogsV1(final String appName, final ApplicationLogListener listener,
			IProgressMonitor monitor) {
		if (appName != null && listener != null) {
			try {
				return new BehaviourRequest<StreamingLogToken>(Messages.ADDING_APPLICATION_LOG_LISTENER, behaviour, v1Client) {
					@Override
					protected StreamingLogToken doRun(CloudFoundryOperations client, SubMonitor progress)
							throws CoreException {
						return client.streamLogs(appName, listener);
					}

				}.run(monitor);
			}
			catch (CoreException e) {
				CloudFoundryPlugin.logError(NLS.bind(Messages.ERROR_APPLICATION_LOG_LISTENER, appName, e.getMessage()),
						e);
			}
		}

		return null;
	}

	public List<ApplicationLog> getRecentLogsV1(String appName, IProgressMonitor monitor) throws CoreException {
		return run(requestFactory.getRecentApplicationLogs(appName), monitor);
	}

	/**
	 * Retrieves the routes for the given domain name; will return early if
	 * cancelled, with an OperationCanceledException.
	 */
	public List<CloudRoute> getRoutesV1(final String domainName, IProgressMonitor monitor) throws CoreException {

		BaseClientRequest<List<CloudRoute>> request = requestFactory.getRoutes(domainName);

		CancellableRequestThread<List<CloudRoute>> t = new CancellableRequestThread<List<CloudRoute>>(request, monitor);
		return t.runAndWaitForCompleteOrCancelled();
	}

	public void deleteRouteV1(List<CloudRoute> routes, IProgressMonitor monitor) throws CoreException {
		run(requestFactory.deleteRoute(routes), monitor);
	}

	public List<CloudDomain> getDomainsForSpaceV1(IProgressMonitor monitor) throws CoreException {
		return run(requestFactory.getDomainsForSpace(), monitor);
	}

	public List<CloudDomain> getDomainsForOrgsV1(IProgressMonitor monitor) throws CoreException {
		return run(requestFactory.getDomainsFromOrgs(), monitor);
	}

	public String getFileV1(CloudApplication app, int instanceIndex, String path, boolean isDir,
			IProgressMonitor monitor) throws CoreException {
		return run(requestFactory.getFile(app, instanceIndex, path, isDir), monitor);
	}

	public List<CloudApplication> getApplicationsV1(IProgressMonitor monitor) throws CoreException {
		return run(requestFactory.getApplications(), monitor);
	}

	public List<CloudApplication> getBasicApplicationsV1(IProgressMonitor monitor) throws CoreException {
		return run(requestFactory.getBasicApplications(), monitor);
	}

	public CFV1Application getCompleteApplicationV1(CloudApplication application, IProgressMonitor monitor)
			throws CoreException {
		return run(requestFactory.getCompleteApplication(application), monitor);
	}

	public ApplicationStats getApplicationStatsV1(String appName, IProgressMonitor monitor) throws CoreException {
		return run(requestFactory.getApplicationStats(appName), monitor);
	}

	public InstancesInfo getInstancesInfoV1(String applicationId, IProgressMonitor monitor) throws CoreException {
		return run(requestFactory.getInstancesInfo(applicationId), monitor);
	}
	
	public CloudApplication getCloudApplicationV1(String appName, IProgressMonitor monitor) throws CoreException {
		return run(requestFactory.getCloudApplication(appName), monitor);
	}

	protected <T> T run(BaseClientRequest<T> request, IProgressMonitor monitor) throws CoreException {
		return request.run(monitor);
	}

	public CloudOrgsAndSpaces getOrgsAndSpacesV1(IProgressMonitor monitor) throws CoreException {
		return run( new BehaviourRequest<CloudOrgsAndSpaces>(Messages.GETTING_ORGS_AND_SPACES, behaviour, v1Client) {

			@Override
			protected CloudOrgsAndSpaces doRun(CloudFoundryOperations client, SubMonitor progress)
					throws CoreException {
				return getCloudSpaces(client);
			}

		}, monitor);
	}
	
	/**
	 * This should be called within a {@link ClientRequest}, as it makes a
	 * direct client call.
	 * @param client
	 * @return
	 */
	public static CloudOrgsAndSpaces getCloudSpaces(CloudFoundryOperations client) {
		List<CloudSpace> foundSpaces = client.getSpaces();
		if (foundSpaces != null && !foundSpaces.isEmpty()) {
			List<CloudSpace> actualSpaces = new ArrayList<CloudSpace>(foundSpaces);
			CloudOrgsAndSpaces orgsAndSpaces = new CloudOrgsAndSpaces(actualSpaces);
			return orgsAndSpaces;
		}

		return null;
	}



}
