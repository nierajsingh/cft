/*******************************************************************************
 * Copyright (c) 2016, 2017 Pivotal Software, Inc. and others 
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

import org.eclipse.cft.server.core.CFServiceInstance;
import org.eclipse.cft.server.core.CFServiceOffering;
import org.eclipse.cft.server.core.EnvironmentVariable;
import org.eclipse.cft.server.core.internal.log.CFApplicationLogListener;
import org.eclipse.cft.server.core.internal.log.CFStreamingLogToken;
import org.eclipse.cft.server.core.internal.log.CloudLog;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * For any unsupported operations, throw {@link UnsupportedOperationException}
 *
 */
public interface CFClient {

	List<CFServiceInstance> deleteServices(List<String> services, IProgressMonitor monitor) throws CoreException;

	List<CFServiceInstance> createServices(CFServiceInstance[] services, IProgressMonitor monitor) throws CoreException;

	void updateEnvironmentVariables(String appName, List<EnvironmentVariable> variables, IProgressMonitor monitor)
			throws CoreException;

	void updateServiceBindings(String appName, List<String> services, IProgressMonitor monitor) throws CoreException;

	void updateAppRoutes(String appName, List<String> urls, IProgressMonitor monitor) throws CoreException;

	void updateApplicationEnableSsh(CloudFoundryApplicationModule appModule, boolean enableSsh,
			IProgressMonitor monitor) throws CoreException;

	void updateApplicationDiego(CloudFoundryApplicationModule appModule, boolean diego, IProgressMonitor monitor)
			throws CoreException;

	void updateApplicationMemory(CloudFoundryApplicationModule appModule, int memory, IProgressMonitor monitor)
			throws CoreException;

	void stopApplication(String message, CloudFoundryApplicationModule cloudModule, IProgressMonitor monitor)
			throws CoreException;

	CFStartingInfo restartApplication(String appName, String startLabel, IProgressMonitor monitor) throws CoreException;

	void register(String email, String password, IProgressMonitor monitor) throws CoreException;

	void updatePassword(String newPassword, IProgressMonitor monitor) throws CoreException;

	void updateApplicationInstances(String appName, int instanceCount, IProgressMonitor monitor) throws CoreException;

	List<CFServiceInstance> getServices(IProgressMonitor monitor) throws CoreException;

	List<CFServiceOffering> getServiceOfferings(IProgressMonitor monitor) throws CoreException;

	void deleteApplication(String appName, IProgressMonitor monitor) throws CoreException;

	boolean reserveRouteIfAvailable(final String host, final String domainName, IProgressMonitor monitor)
			throws CoreException;

	List<CFCloudDomain> getDomainsForSpace(IProgressMonitor monitor) throws CoreException;

	List<CFCloudDomain> getDomainsForOrgs(IProgressMonitor monitor) throws CoreException;

	List<String> getBuildpacks(IProgressMonitor monitor) throws CoreException;

	void deleteRoute(String host, String domainName, IProgressMonitor monitor) throws CoreException;

	List<CloudLog> getRecentLogs(String appName, IProgressMonitor monitor) throws CoreException;

	CFStreamingLogToken streamLogs(String appName, CFApplicationLogListener listener, IProgressMonitor monitor)
			throws CoreException;

	String login(IProgressMonitor monitor) throws CoreException;

}
