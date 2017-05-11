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

import java.util.List;
import java.util.concurrent.Callable;

import org.eclipse.cft.server.core.CFServiceInstance;
import org.eclipse.cft.server.core.CFServiceOffering;
import org.eclipse.cft.server.core.EnvironmentVariable;
import org.eclipse.cft.server.core.internal.CloudErrorUtil;
import org.eclipse.cft.server.core.internal.log.CFApplicationLogListener;
import org.eclipse.cft.server.core.internal.log.CFStreamingLogToken;
import org.eclipse.cft.server.core.internal.log.CloudLog;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * 
 * A wrapper around the {@link CFClient} used by the CFT framework that handles
 * both legacy v1 CF Java client for backward compatibility and client load from
 * the CFT client framework
 * <p/>
 * Instances of this client should only be accessed through the
 * {@link CloudFoundryServerBehaviour}.
 * <p/>
 * {@link CloudFoundryServerBehaviour#getBehaviourClient(IProgressMonitor)}
 */
public class CFBehaviourClient implements CFClient {

	private V1CFClient v1Client;

	private CFClient actualClient;

	public CFBehaviourClient(V1CFClient v1Client, CFClient actualClient) {
		this.v1Client = v1Client;
		this.actualClient = actualClient;
	}

	@Override
	public String login(IProgressMonitor monitor) throws CoreException {
		return supply(() -> actualClient.login(monitor), () -> v1Client.login(monitor));
	}

	@Override
	public CFStreamingLogToken streamLogs(String appName, CFApplicationLogListener listener, IProgressMonitor monitor)
			throws CoreException {
		return supply(() -> actualClient.streamLogs(appName, listener, monitor),
				() -> v1Client.streamLogs(appName, listener, monitor));
	}

	@Override
	public List<CloudLog> getRecentLogs(String appName, IProgressMonitor monitor) throws CoreException {
		return supply(() -> actualClient.getRecentLogs(appName, monitor),
				() -> v1Client.getRecentLogs(appName, monitor));
	}

	@Override
	public void deleteRoute(String host, String domainName, IProgressMonitor monitor) throws CoreException {
		run(() -> actualClient.deleteRoute(host, domainName, monitor),
				() -> v1Client.deleteRoute(host, domainName, monitor));
	}

	@Override
	public List<String> getBuildpacks(IProgressMonitor monitor) throws CoreException {
		return supply(() -> actualClient.getBuildpacks(monitor), () -> v1Client.getBuildpacks(monitor));
	}

	@Override
	public List<CFCloudDomain> getDomainsForOrgs(IProgressMonitor monitor) throws CoreException {
		return supply(() -> actualClient.getDomainsForOrgs(monitor), () -> v1Client.getDomainsForOrgs(monitor));
	}

	@Override
	public List<CFCloudDomain> getDomainsForSpace(IProgressMonitor monitor) throws CoreException {
		return supply(() -> actualClient.getDomainsForSpace(monitor), () -> v1Client.getDomainsForSpace(monitor));
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
		return supply(() -> actualClient.reserveRouteIfAvailable(host, domainName, monitor),
				() -> v1Client.reserveRouteIfAvailable(host, domainName, monitor));
	}

	@Override
	public void deleteApplication(String appName, IProgressMonitor monitor) throws CoreException {
		run(() -> actualClient.deleteApplication(appName, monitor), () -> v1Client.deleteApplication(appName, monitor));
	}

	@Override
	public List<CFServiceOffering> getServiceOfferings(IProgressMonitor monitor) throws CoreException {
		return supply(() -> actualClient.getServiceOfferings(monitor), () -> v1Client.getServiceOfferings(monitor));
	}

	@Override
	public List<CFServiceInstance> getServices(IProgressMonitor monitor) throws CoreException {
		return supply(() -> actualClient.getServices(monitor), () -> v1Client.getServices(monitor));
	}

	@Override
	public void updateApplicationInstances(String appName, int instanceCount, IProgressMonitor monitor)
			throws CoreException {
		run(() -> actualClient.updateApplicationInstances(appName, instanceCount, monitor),
				() -> v1Client.updateApplicationInstances(appName, instanceCount, monitor));
	}

	@Override
	public void updatePassword(String newPassword, IProgressMonitor monitor) throws CoreException {
		run(() -> actualClient.updatePassword(newPassword, monitor),
				() -> v1Client.updatePassword(newPassword, monitor));
	}

	@Override
	public void register(String email, String password, IProgressMonitor monitor) throws CoreException {
		run(() -> actualClient.register(email, password, monitor), () -> v1Client.register(email, password, monitor));
	}

	@Override
	public CFStartingInfo restartApplication(String appName, String startLabel, IProgressMonitor monitor)
			throws CoreException {
		return supply(() -> actualClient.restartApplication(appName, startLabel, monitor),
				() -> v1Client.restartApplication(appName, startLabel, monitor));
	}

	@Override
	public void stopApplication(String message, CloudFoundryApplicationModule cloudModule, IProgressMonitor monitor)
			throws CoreException {
		run(() -> actualClient.stopApplication(message, cloudModule, monitor),
				() -> v1Client.stopApplication(message, cloudModule, monitor));
	}

	@Override
	public void updateApplicationMemory(CloudFoundryApplicationModule appModule, int memory, IProgressMonitor monitor)
			throws CoreException {
		run(() -> actualClient.updateApplicationMemory(appModule, memory, monitor),
				() -> v1Client.updateApplicationMemory(appModule, memory, monitor));
	}

	@Override
	public void updateApplicationDiego(CloudFoundryApplicationModule appModule, boolean diego, IProgressMonitor monitor)
			throws CoreException {
		run(() -> actualClient.updateApplicationDiego(appModule, diego, monitor),
				() -> v1Client.updateApplicationDiego(appModule, diego, monitor));
	}

	@Override
	public void updateApplicationEnableSsh(CloudFoundryApplicationModule appModule, boolean enableSsh,
			IProgressMonitor monitor) throws CoreException {
		run(() -> actualClient.updateApplicationEnableSsh(appModule, enableSsh, monitor),
				() -> v1Client.updateApplicationEnableSsh(appModule, enableSsh, monitor));
	}

	@Override
	public void updateAppRoutes(String appName, List<String> urls, IProgressMonitor monitor) throws CoreException {
		run(() -> actualClient.updateAppRoutes(appName, urls, monitor),
				() -> v1Client.updateAppRoutes(appName, urls, monitor));
	}

	@Override
	public void updateServiceBindings(String appName, List<String> services, IProgressMonitor monitor)
			throws CoreException {
		run(() -> actualClient.updateServiceBindings(appName, services, monitor),
				() -> v1Client.updateServiceBindings(appName, services, monitor));
	}

	@Override
	public void updateEnvironmentVariables(String appName, List<EnvironmentVariable> variables,
			IProgressMonitor monitor) throws CoreException {
		run(() -> actualClient.updateEnvironmentVariables(appName, variables, monitor),
				() -> v1Client.updateEnvironmentVariables(appName, variables, monitor));
	}

	@Override
	public List<CFServiceInstance> createServices(CFServiceInstance[] services, IProgressMonitor monitor)
			throws CoreException {
		return supply(() -> actualClient.createServices(services, monitor),
				() -> v1Client.createServices(services, monitor));
	}

	@Override
	public List<CFServiceInstance> deleteServices(List<String> services, IProgressMonitor monitor)
			throws CoreException {
		return supply(() -> actualClient.deleteServices(services, monitor),
				() -> v1Client.deleteServices(services, monitor));
	}

	protected <R> R supply(Callable<R> actualClientCallable, Callable<R> v1ClientCallable) throws CoreException {
		try {
			if (this.actualClient == null) {
				return v1ClientCallable.call();
			}
			else {
				try {
					return actualClientCallable.call();
				}
				catch (UnsupportedOperationException notSupported) {
					return v1ClientCallable.call();
				}
			}
		}
		catch (Exception e) {
			throw CloudErrorUtil.toCoreException(e);
		}
	}

	protected void run(RequestRunnable actualClientRunnable, RequestRunnable v1ClientRunnable) throws CoreException {
		if (this.actualClient == null) {
			v1ClientRunnable.run();
		}
		else {
			try {
				actualClientRunnable.run();
			}
			catch (UnsupportedOperationException notSupported) {
				v1ClientRunnable.run();
			}
		}
	}

	public V1CFClient getV1Client() {
		return v1Client;
	}

	@FunctionalInterface
	static interface RequestRunnable {
		void run() throws CoreException;
	}

}
