/*******************************************************************************
 * Copyright (c) 2012, 2017 Pivotal Software, Inc., IBM Corporation, and others
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
 *     IBM - wait for all module publish complete before finish up publish operation.
 *           Bug 485697 - Implement host name taken check in CF wizards
 ********************************************************************************/
package org.eclipse.cft.server.core.internal.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.cloudfoundry.client.lib.ApplicationLogListener;
import org.cloudfoundry.client.lib.CloudCredentials;
import org.cloudfoundry.client.lib.CloudFoundryException;
import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.cloudfoundry.client.lib.StreamingLogToken;
import org.cloudfoundry.client.lib.domain.ApplicationLog;
import org.cloudfoundry.client.lib.domain.ApplicationStats;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.client.lib.domain.CloudDomain;
import org.cloudfoundry.client.lib.domain.CloudRoute;
import org.cloudfoundry.client.lib.domain.InstancesInfo;
import org.eclipse.cft.server.core.ApplicationDeploymentInfo;
import org.eclipse.cft.server.core.CFApplicationArchive;
import org.eclipse.cft.server.core.CFServiceInstance;
import org.eclipse.cft.server.core.CFServiceOffering;
import org.eclipse.cft.server.core.ISshClientSupport;
import org.eclipse.cft.server.core.internal.ApplicationAction;
import org.eclipse.cft.server.core.internal.ApplicationInstanceRunningTracker;
import org.eclipse.cft.server.core.internal.ApplicationUrlLookupService;
import org.eclipse.cft.server.core.internal.CloudErrorUtil;
import org.eclipse.cft.server.core.internal.CloudFoundryPlugin;
import org.eclipse.cft.server.core.internal.CloudFoundryServer;
import org.eclipse.cft.server.core.internal.CloudServerEvent;
import org.eclipse.cft.server.core.internal.CloudUtil;
import org.eclipse.cft.server.core.internal.Messages;
import org.eclipse.cft.server.core.internal.ModuleResourceDeltaWrapper;
import org.eclipse.cft.server.core.internal.OperationScheduler;
import org.eclipse.cft.server.core.internal.ServerEventHandler;
import org.eclipse.cft.server.core.internal.UpdateOperationsScheduler;
import org.eclipse.cft.server.core.internal.application.ApplicationRegistry;
import org.eclipse.cft.server.core.internal.application.CachingApplicationArchive;
import org.eclipse.cft.server.core.internal.debug.ApplicationDebugLauncher;
import org.eclipse.cft.server.core.internal.jrebel.CFRebelServerIntegration;
import org.eclipse.cft.server.core.internal.log.CFApplicationLogListener;
import org.eclipse.cft.server.core.internal.log.CFStreamingLogToken;
import org.eclipse.cft.server.core.internal.log.CloudLog;
import org.eclipse.cft.server.core.internal.spaces.CloudOrgsAndSpaces;
import org.eclipse.cft.server.core.internal.ssh.SshClientSupport;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jst.server.core.IWebModule;
import org.eclipse.osgi.util.NLS;
import org.eclipse.wst.server.core.IModule;
import org.eclipse.wst.server.core.IServer;
import org.eclipse.wst.server.core.IServerListener;
import org.eclipse.wst.server.core.ServerEvent;
import org.eclipse.wst.server.core.internal.IModuleVisitor;
import org.eclipse.wst.server.core.internal.Server;
import org.eclipse.wst.server.core.internal.ServerPublishInfo;
import org.eclipse.wst.server.core.model.IModuleFile;
import org.eclipse.wst.server.core.model.IModuleResource;
import org.eclipse.wst.server.core.model.IModuleResourceDelta;
import org.eclipse.wst.server.core.model.ServerBehaviourDelegate;
import org.springframework.http.HttpStatus;

/**
 * 
 * This is the primary interface to the underlying Java Cloud client that
 * performs actual requests to a target Cloud space. It contains API to start,
 * stop, restart, and publish applications to a Cloud space, as well as scale
 * application memory, instances, set environment variables and map application
 * URLs.
 * <p/>
 * This is intended as a lower-level interface to interact with the underlying
 * client, as well as to integrate with WST framework
 * <p/>
 * However, the majority of these operations require additional functionality
 * that are specific to the Cloud tooling, like firing refresh and server change
 * events. Therefore, it is advisable to obtain the appropriate
 * {@link ICloudFoundryOperation} for these operations through
 * {@link #operations()}.
 * <p/>
 * It's important to note that as of CF 1.6.1, all WST framework-based
 * publishing will result in server-level publishing, so even if deploying a
 * particular application, other applications that are already deployed and not
 * external (i.e. have a corresponding workspace project) that need republishing
 * may be republished as well.
 * 
 * IMPORTANT NOTE: This class can be referred by the branding extension from
 * adopter so this class should not be moved or renamed to avoid breakage to
 * adopters.
 * 
 * @author Christian Dupuis
 * @author Leo Dos Santos
 * @author Terry Denney
 * @author Steffen Pingel
 * @author Nieraj Singh
 */
@SuppressWarnings("restriction")
public class CloudFoundryServerBehaviour extends ServerBehaviourDelegate {

	private CFBehaviourClient behaviourClient;
	
	private UpdateOperationsScheduler operationsScheduler;

	private ApplicationUrlLookupService applicationUrlLookup;

	private CloudBehaviourOperations cloudBehaviourOperations;

	private IServerListener serverListener = new IServerListener() {

		public void serverChanged(ServerEvent event) {
			if (event.getKind() == ServerEvent.SERVER_CHANGE) {
				// reset client to consume updated credentials at a later stage.
				// Do not connect
				// right away
				//
				internalResetClient();
			}
		}
	};

	protected enum DebugSupportCheck {
		// Initial state of the debug support check. used so that further checks
		// are not necessary in a given session
		UNCHECKED,
		// Server supports debug mode
		SUPPORTED,
		// Server does not support debug mode
		UNSUPPORTED,
	}

	@Override
	public boolean canControlModule(IModule[] module) {
		return module.length == 1;
	}

	/**
	 * When calling connect(...) the client field of CloudFoundryServerBehavoiur
	 * must already be set.
	 */
	public void connect(IProgressMonitor monitor) throws CoreException {
		final CloudFoundryServer cloudServer = getCloudFoundryServer();

		getBehaviourClient(monitor).login(monitor);

		Server server = (Server) cloudServer.getServerOriginal();
		server.setServerState(IServer.STATE_STARTED);
		server.setServerPublishState(IServer.PUBLISH_STATE_NONE);

		getApplicationUrlLookup().refreshDomains(monitor);

		asyncUpdateAll();

		ServerEventHandler.getDefault().fireServerEvent(
				new CloudServerEvent(getCloudFoundryServer(), CloudServerEvent.EVENT_SERVER_CONNECTED));
	}

	/** The user is providing us with a new passcode, so acquire the token 
	 * <p/>
	 * This has been deprecated as it uses v1 CF Java client only.
	 * 
	 * */
	@Deprecated
	public boolean regenerateSsoLogin(String passcode, IProgressMonitor monitor) throws CoreException {

		final CloudFoundryServer cloudServer = getCloudFoundryServer();

		// Log in and acquire the token
		createExternalClientLogin(cloudServer, cloudServer.getUrl(), null, null, cloudServer.isSelfSigned(), true,
				cloudServer.getPasscode(), null, monitor);

		// Ensure that client field is set, before connect call
		 CFBehaviourClient result = getOrCreateBehaviourClient(true, monitor);

		return result != null;

	}

	/**
	 * Cloud operations ( {@link ICloudFoundryOperation} ) that can be performed
	 * on the Cloud space targeted by the server behaviour.
	 * @return Non-Null Cloud Operations
	 */
	public CloudBehaviourOperations operations() {
		if (cloudBehaviourOperations == null) {
			cloudBehaviourOperations = new CloudBehaviourOperations(this);
		}
		return cloudBehaviourOperations;
	}

	/**
	 * Schedules asynchronous update operations for the Cloud server behaviour.
	 * {@link #cloudBehaviourOperations}. For synchronous execution, use
	 * {@link #operations()}
	 * @return Non-null scheduler
	 */
	private synchronized UpdateOperationsScheduler getUpdateModulesScheduler() {
		return (UpdateOperationsScheduler) getOperationScheduler();
	}

	public synchronized OperationScheduler getOperationScheduler() {

		if (operationsScheduler == null) {
			CloudFoundryServer server = null;
			try {
				server = getCloudFoundryServer();
			}
			catch (CoreException ce) {
				CloudFoundryPlugin.logError(ce);
			}
			operationsScheduler = new UpdateOperationsScheduler(server);
		}
		return operationsScheduler;

	}

	/**
	 * 
	 * @return non-null debug launcher
	 */
	public ApplicationDebugLauncher getDebugLauncher() {
		try {
			return CloudFoundryPlugin.getCallback().getDebugLauncher(getCloudFoundryServer());
		}
		catch (CoreException e) {
			CloudFoundryPlugin.logError(e);
		}
		return ApplicationDebugLauncher.NO_DEBUG;
	}

	@Deprecated
	public synchronized List<CloudDomain> getDomainsFromOrgs(IProgressMonitor monitor) throws CoreException {
		return getBehaviourClient(monitor).getV1Client().getDomainsForOrgsV1(monitor);
	}

	@Deprecated
	public synchronized List<CloudDomain> getDomainsForSpace(IProgressMonitor monitor) throws CoreException {
		return getBehaviourClient(monitor).getV1Client().getDomainsForSpaceV1(monitor);
	}

	/**
	 * Deletes the given modules. Note that any refresh job is stopped while
	 * this operation is running, and restarted after its complete.
	 * @deprecated use {@link #operations()} instead
	 * @param modules
	 * @param deleteServices
	 * @param monitor
	 * @throws CoreException
	 */
	public void deleteModules(final IModule[] modules, final boolean deleteServices, IProgressMonitor monitor)
			throws CoreException {
		operations().deleteModules(modules, deleteServices).run(monitor);
	}

	/**
	 * Deletes a cloud application in the target Cloud space. May throw
	 * {@link CoreException} if the application no longer exists, or failed to
	 * delete..
	 * @param appName
	 * @param monitor
	 * @throws CoreException
	 */
	public void deleteApplication(String appName, IProgressMonitor monitor) throws CoreException {
		getBehaviourClient(monitor).deleteApplication(appName, monitor);
	}

	/**
	 * Deletes the list of services.
	 * @deprecated use {@link #operations()} instead
	 * @param services
	 * @throws CoreException if error occurred during service deletion.
	 */
	public ICloudFoundryOperation getDeleteServicesOperation(final List<String> services) throws CoreException {
		return operations().deleteServices(services);
	}

	/**
	 * The Cloud application URL lookup is used to resolve a list of URL domains
	 * that an application can user when specifying a URL.
	 * <p/>
	 * Note that this only returns a cached lookup. The lookup may have to be
	 * refreshed separately to get the most recent list of domains.
	 * @return Lookup to retrieve list of application URL domains, as well as
	 * verify validity of an application URL. May be null as its a cached
	 * version.
	 */
	public ApplicationUrlLookupService getApplicationUrlLookup() {
		if (applicationUrlLookup == null) {
			try {
				applicationUrlLookup = new ApplicationUrlLookupService(getCloudFoundryServer());
			}
			catch (CoreException e) {
				CloudFoundryPlugin
						.logError("Failed to create the Cloud Foundry Application URL lookup service due to {" + //$NON-NLS-1$
								e.getMessage(), e);
			}
		}
		return applicationUrlLookup;
	}

	protected List<IModuleResource> getChangedResources(IModuleResourceDelta[] deltas) {
		List<IModuleResource> changed = new ArrayList<IModuleResource>();
		if (deltas != null) {
			findNonChangedResources(deltas, changed);
		}
		return changed;

	}

	protected void findNonChangedResources(IModuleResourceDelta[] deltas, List<IModuleResource> changed) {
		if (deltas == null || deltas.length == 0) {
			return;
		}
		for (IModuleResourceDelta delta : deltas) {
			// Only handle file resources
			IModuleResource resource = delta.getModuleResource();
			if (resource instanceof IModuleFile && delta.getKind() != IModuleResourceDelta.NO_CHANGE) {
				changed.add(new ModuleResourceDeltaWrapper(delta));
			}

			findNonChangedResources(delta.getAffectedChildren(), changed);
		}
	}

	/**
	 * Disconnects the local server from the remote CF server, and terminate the
	 * session. Note that this will stop any refresh operations, or console
	 * streaming, but will NOT stop any apps that are currently running. It may
	 * also clear any application module caches associated with the session.
	 * @param monitor
	 * @throws CoreException
	 */
	public void disconnect(IProgressMonitor monitor) throws CoreException {
		CloudFoundryPlugin.getCallback().disconnecting(getCloudFoundryServer());

		Server server = (Server) getServer();
		server.setServerState(IServer.STATE_STOPPING);

		CloudFoundryServer cloudServer = getCloudFoundryServer();

		Collection<CloudFoundryApplicationModule> cloudModules = cloudServer.getExistingCloudModules();

		for (CloudFoundryApplicationModule appModule : cloudModules) {
			CloudFoundryPlugin.getCallback().stopApplicationConsole(appModule, cloudServer);
		}

		Set<CloudFoundryApplicationModule> deletedModules = new HashSet<CloudFoundryApplicationModule>(cloudModules);

		cloudServer.clearApplications();

		// update state for cloud applications
		server.setExternalModules(new IModule[0]);
		for (CloudFoundryApplicationModule module : deletedModules) {
			server.setModuleState(new IModule[] { module.getLocalModule() }, IServer.STATE_UNKNOWN);
		}

		server.setServerState(IServer.STATE_STOPPED);
		server.setServerPublishState(IServer.PUBLISH_STATE_NONE);

		ServerEventHandler.getDefault().fireServerEvent(
				new CloudServerEvent(getCloudFoundryServer(), CloudServerEvent.EVENT_SERVER_DISCONNECTED));
	}

	public void reconnect(IProgressMonitor monitor) throws CoreException {
		final SubMonitor subMonitor = SubMonitor.convert(monitor, 100);

		subMonitor.subTask(NLS.bind(Messages.CloudFoundryServerBehaviour_RECONNECTING_SERVER,
				getCloudFoundryServer().getServer().getId()));

		disconnect(subMonitor.newChild(40));

		try {
			resetClient(subMonitor.newChild(20));
		}
		catch (CloudFoundryException cfe) {
			throw CloudErrorUtil.toCoreException(cfe);
		}

		connect(subMonitor.newChild(40));
	}

	@Override
	public void dispose() {
		super.dispose();
		getServer().removeServerListener(serverListener);
	}

	/**
	 * This method is API used by CloudFoundry Code.
	 */
	public CloudFoundryServer getCloudFoundryServer() throws CoreException {
		Server server = (Server) getServer();

		CloudFoundryServer cloudFoundryServer = (CloudFoundryServer) server.loadAdapter(CloudFoundryServer.class, null);
		if (cloudFoundryServer == null) {
			throw new CoreException(new Status(IStatus.ERROR, CloudFoundryPlugin.PLUGIN_ID, "Fail to load server")); //$NON-NLS-1$
		}
		return cloudFoundryServer;
	}

	/**
	 * @deprecated use {@link #getCloudApplication(String, IProgressMonitor)}
	 * @param appName
	 * @param monitor
	 * @return
	 * @throws CoreException
	 */
	public CloudApplication getApplication(final String appName, IProgressMonitor monitor) throws CoreException {
		return getCloudApplication(appName, monitor);
	}

	/**
	 * Fetches an updated {@link CloudApplication} from the target Cloud space.
	 * 
	 * <p/>
	 * Note that his is a lower-level model of the application as presented by
	 * the underlying Cloud Java client and does not contain additional API used
	 * by the WST framework (for example, checking publish state or references
	 * to the module's workspace project and resources) or the Cloud tooling
	 * e.g. {@link DeploymentConfiguration}.
	 * 
	 * <p/>
	 * To obtain the application's associated module with the additional API,
	 * use {@link #updateModuleWithBasicCloudInfo(String, IProgressMonitor)}
	 * @param appName
	 * @param monitor
	 * @return Cloud application. If null it may indicate that the application
	 * is not yet available
	 * @throws CoreException if error occurs while resolving the Cloud
	 * application, or the application does not exist.
	 */
	public CloudApplication getCloudApplication(final String appName, IProgressMonitor monitor) throws CoreException {
		return getBehaviourClient(monitor).getV1Client().getCloudApplicationV1(appName, monitor);
	}

	/**
	 * Update the given module with application stats and instance information
	 * obtained from the Cloud space. Will only update the module if it is
	 * deployed.
	 * 
	 * @param appName
	 * @param monitor
	 * @return cloud module with updated instances for the give app name, or
	 * null if the app does not exist
	 * @throws CoreException
	 */
	public CloudFoundryApplicationModule updateDeployedModule(IModule module, IProgressMonitor monitor)
			throws CoreException {
		CloudFoundryApplicationModule appModule = getCloudFoundryServer().getExistingCloudModule(module);
		// Note: the isDeployed check is for:
		// [485228] Attempting to publish (then cancelling) a Web project with
		// the
		// same name as a running Bluemix app. Take care NOT to modify this
		// without thorough testing
		if (appModule != null && appModule.isDeployed()) {
			String name = appModule.getDeployedApplicationName();
			if (name != null) {
				return updateModuleWithAllCloudInfo(name, monitor);
			}
		}

		return null;
	}

	/**
	 * Updates the given module with complete Cloud information about the
	 * application. This is different than
	 * {@link #updateDeployedModule(IModule, IProgressMonitor)} in the sense
	 * that deployment state is not a factor to determine if information about
	 * the associated Cloud application needs to be fetched. This should be used
	 * in case there is no known information about whether the application
	 * exists or is running, and complete information about the application in
	 * the Cloud is needed, including information on instances.
	 * @param module
	 * @param monitor
	 * @return
	 * @throws CoreException
	 */
	public CloudFoundryApplicationModule updateModuleWithAllCloudInfo(IModule module, IProgressMonitor monitor)
			throws CoreException {
		CloudFoundryApplicationModule appModule = getCloudFoundryServer().getExistingCloudModule(module);

		if (appModule != null) {
			String name = appModule.getDeployedApplicationName();
			if (name != null) {
				return updateModuleWithAllCloudInfo(name, monitor);
			}
		}

		return null;
	}

	/**
	 * Updates a module with enough Cloud information to determine basic Cloud
	 * application stats (URL, bound services, env vars, etc..) including its
	 * running state in the Cloud. Returns null if the application no longer
	 * exists
	 * @param module
	 * @param monitor
	 * @return Updated {@link CloudFoundryApplicationModule} or null if the
	 * application no longer exists in the Cloud
	 * @throws CoreException
	 */
	public CloudFoundryApplicationModule updateModuleWithBasicCloudInfo(IModule module, IProgressMonitor monitor)
			throws CoreException {

		CloudFoundryApplicationModule appModule = getCloudFoundryServer().getExistingCloudModule(module);

		String name = appModule != null ? appModule.getDeployedApplicationName() : module.getName();
		return updateModuleWithBasicCloudInfo(name, monitor);
	}

	/**
	 * Updates a module with enough Cloud information to determine basic Cloud
	 * application stats (URL, bound services, env vars, etc..) including its
	 * running state in the Cloud. Returns null if the application no longer
	 * exists.
	 * @param module
	 * @param monitor
	 * @return Updated {@link CloudFoundryApplicationModule} or null if the
	 * application no longer exists in the Cloud Space
	 * @throws CoreException if error occurs while resolving an updated
	 * {@link CloudApplication} from the Cloud space
	 */
	public CloudFoundryApplicationModule updateModuleWithBasicCloudInfo(String appName, IProgressMonitor monitor)
			throws CoreException {
		CloudApplication updatedApp = null;
		SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
		subMonitor.subTask(NLS.bind(Messages.CloudFoundryServer_UPDATING_MODULE, appName, getServer().getId()));
		ApplicationStats stats = null;
		try {
			updatedApp = getCloudApplication(appName, subMonitor.newChild(50));
			if (updatedApp != null) {
				stats = getApplicationStats(appName, monitor);
			}
		}
		catch (CoreException e) {
			// Ignore if it is application not found error. If the application
			// does not exist
			// anymore, update the modules accordingly
			if (!CloudErrorUtil.isNotFoundException(e)) {
				throw e;
			}
		}
		return getCloudFoundryServer().updateModule(updatedApp, appName, stats, subMonitor.newChild(50));
	}

	/**
	 * Update the given module with complete application information including
	 * instances info
	 *
	 * @param appName
	 * @param monitor
	 * @return cloud module with updated instances for the give app name, or
	 * null if the app does not exist
	 * @throws CoreException
	 */
	public CloudFoundryApplicationModule updateModuleWithAllCloudInfo(String appName, IProgressMonitor monitor)
			throws CoreException {
		SubMonitor subMonitor = SubMonitor.convert(monitor, 100);

		CloudFoundryApplicationModule appModule = updateModuleWithBasicCloudInfo(appName, subMonitor.newChild(50));

		// App may no longer exist after update. Therefore guard against a null Cloud module
		if (appModule != null) {
			updateInstancesInfo(appModule, subMonitor.newChild(50));
			appModule.validateAndUpdateStatus();
		}
		return appModule;
	}

	/**
	 * Resets the module state of the module and all its children
	 */
	public void cleanModuleStates(IModule[] modules, IProgressMonitor monitor) {
		IServer iServer = this.getServer();
		if (iServer != null && iServer instanceof Server) {
			Server server = (Server) iServer;
			final ServerPublishInfo info = server.getServerPublishInfo();

			info.startCaching();
			info.clearCache();

			info.fill(modules);

			// The visit below will iterate through all children modules
			final List<IModule[]> modules2 = new ArrayList<IModule[]>();
			server.visit(new IModuleVisitor() {
				public boolean visit(IModule[] module) {
					info.fill(module);
					modules2.add(module);
					return true;
				}
			}, monitor);

			info.removeDeletedModulePublishInfo(server, modules2);

			info.save();
			super.setModulePublishState(modules, IServer.PUBLISH_STATE_NONE);
		}
	}

	/**
	 * Updates additional instances information for the given Cloud module. If
	 * the module is null, nothing will happen.
	 * @param appModule
	 * @param monitor
	 * @throws CoreException
	 */
	private void updateInstancesInfo(CloudFoundryApplicationModule appModule, IProgressMonitor monitor)
			throws CoreException {
		// Module may have been deleted and application no longer exists.
		// Nothing to update
		if (appModule == null) {
			return;
		}
		try {
			InstancesInfo info = getInstancesInfo(appModule.getDeployedApplicationName(), monitor);
			appModule.setInstancesInfo(info);
		}
		catch (CoreException e) {
			// Ignore if it is application not found error. If the application
			// does not exist
			// anymore, update the modules accordingly
			if (!CloudErrorUtil.isNotFoundException(e)) {
				throw e;
			}
		}
	}

	/**
	 * Fetches list of all applications in the Cloud space. No module updates
	 * occur, as this is a low-level API meant to interact with the underlying
	 * client directly. Callers should be responsible to update associated
	 * modules. Note that this may be a long-running operation. If fetching a
	 * known application , it is recommended to call
	 * {@link #getCloudApplication(String, IProgressMonitor)} or
	 * {@link #updateModuleWithBasicCloudInfo(IModule, IProgressMonitor)} as it
	 * may be potentially faster
	 * @param monitor
	 * @return List of all applications in the Cloud space.
	 * @throws CoreException
	 */
	@Deprecated
	public List<CloudApplication> getApplications(IProgressMonitor monitor) throws CoreException {
		return getBehaviourClient(monitor).getV1Client().getApplicationsV1(monitor);
	}

	@Deprecated
	public List<CloudApplication> getBasicApplications(IProgressMonitor monitor) throws CoreException {
		return getBehaviourClient(monitor).getV1Client().getBasicApplicationsV1(monitor);
	}

	@Deprecated
	public CFV1Application getCompleteApplication(CloudApplication application, IProgressMonitor monitor)
			throws CoreException {
		return getBehaviourClient(monitor).getV1Client().getCompleteApplicationV1(application, monitor);
	}

	@Deprecated
	public ApplicationStats getApplicationStats(String appName, IProgressMonitor monitor) throws CoreException {
		return getBehaviourClient(monitor).getV1Client().getApplicationStatsV1(appName, monitor);
	}

	@Deprecated
	public InstancesInfo getInstancesInfo(final String applicationId, IProgressMonitor monitor) throws CoreException {
		return getBehaviourClient(monitor).getV1Client().getInstancesInfoV1(applicationId, monitor);
	}

	@Deprecated
	public String getFile(CloudApplication app, int instanceIndex, String path, boolean isDir, IProgressMonitor monitor)
			throws CoreException {
		return getBehaviourClient(monitor).getV1Client().getFileV1(app, instanceIndex, path, isDir, monitor);
	}

	public List<CFServiceOffering> getServiceOfferings(IProgressMonitor monitor) throws CoreException {
		return getBehaviourClient(monitor).getServiceOfferings(monitor);
	}

	public List<CFServiceInstance> getServices(IProgressMonitor monitor) throws CoreException {
		return getBehaviourClient(monitor).getServices(monitor);
	}

	/**
	 * Refresh the application modules and reschedules the app module refresh
	 * job to execute at certain intervals. This will synch all local
	 * application modules with the actual deployed applications. This may be a
	 * long running operation.
	 * @deprecated user {@link #getUpdateModulesScheduler()} instead
	 * @param monitor
	 */
	public void refreshModules(IProgressMonitor monitor) {
		asyncUpdateAll();
	}

	/**
	 * Resets the client. Note that any cached information used by the previous
	 * client will be cleared. Credentials used to reset the client will be
	 * retrieved from the the local server store.
	 * @param monitor
	 * @throws CoreException failure to reset client, disconnect using current
	 * client, or login/connect to the server using new client
	 */
	public CFClient resetClient(IProgressMonitor monitor)
			throws CoreException {
		internalResetClient();
		return getOrCreateBehaviourClient(false, monitor);
	}

	protected void internalResetClient() {
		behaviourClient = null;
		applicationUrlLookup = null;
		cloudBehaviourOperations = null;
		operationsScheduler = null;
	}

	@Override
	public void startModule(IModule[] modules, IProgressMonitor monitor) throws CoreException {
		operations().applicationDeployment(modules, ApplicationAction.RESTART).run(monitor);
	}

	@Override
	public void stop(boolean force) {
		// This stops the server locally, it does NOT stop the remotely running
		// applications
		setServerState(IServer.STATE_STOPPED);

		try {
			ServerEventHandler.getDefault().fireServerEvent(
					new CloudServerEvent(getCloudFoundryServer(), CloudServerEvent.EVENT_SERVER_DISCONNECTED));
		}
		catch (CoreException e) {
			CloudFoundryPlugin.logError(e);
		}
	}

	@Override
	public void stopModule(IModule[] modules, IProgressMonitor monitor) throws CoreException {
		operations().applicationDeployment(modules, ApplicationAction.STOP).run(monitor);
	}

	/**
	 * @deprecated use {@link #operations()} instead
	 * @param modules
	 * @return
	 */
	public ICloudFoundryOperation getStopAppOperation(IModule[] modules) {
		try {
			return operations().applicationDeployment(modules, ApplicationAction.STOP);
		}
		catch (CoreException e) {
			CloudFoundryPlugin.logError(e);
		}
		return null;
	}

	/**
	 * 
	 * @param appName
	 * @param listener
	 * @return
	 * @deprecated This is for app streaming using the old CF Java client
	 * version v1. Use
	 * {@link #startAppLogStreaming(String, CFApplicationLogListener)}
	 * instead.
	 */
	public StreamingLogToken addApplicationLogListener(final String appName, final ApplicationLogListener listener) {
		try {
			return getBehaviourClient(new NullProgressMonitor()).getV1Client().streamLogsV1(appName, listener, new NullProgressMonitor());
		}
		catch (CoreException e) {
			CloudFoundryPlugin.logError(e);
		}
		return null;
	}

	/**
	 * 
	 * @param appName
	 * @param monitor
	 * @return
	 * @throws CoreException
	 * @deprecated
	 */
	public List<ApplicationLog> getRecentApplicationLogs(final String appName, IProgressMonitor monitor)
			throws CoreException {
		return getBehaviourClient(monitor).getV1Client().getRecentLogsV1(appName, monitor);
	}

	/**
	 * Note that this automatically restarts a module in the start mode it is
	 * currently, or was currently running in. It automatically detects if an
	 * application is running in debug mode or regular run mode, and restarts it
	 * in that same mode. Other API exists to restart an application in a
	 * specific mode, if automatic detection and restart in existing mode is not
	 * required.
	 */
	@Override
	public void restartModule(IModule[] modules, IProgressMonitor monitor) throws CoreException {
		operations().applicationDeployment(modules, ApplicationAction.RESTART).run(monitor);
	}

	/**
	 * Update restart republishes redeploys the application with changes. This
	 * is not the same as restarting an application which simply restarts the
	 * application in its current server version without receiving any local
	 * changes. It will only update restart an application in regular run mode.
	 * It does not support debug mode.Publishing of changes is done
	 * incrementally.
	 * @deprecated use {@link #operations()} instead
	 * @param module to update
	 * @throws CoreException
	 */
	public ICloudFoundryOperation getUpdateRestartOperation(IModule[] modules) throws CoreException {
		return operations().applicationDeployment(modules, ApplicationAction.UPDATE_RESTART);
	}

	/**
	 * This will restart an application in run mode. It does not restart an
	 * application in debug mode. Does not push application resources or create
	 * the application. The application must exist in the CloudFoundry server.
	 * @deprecated user {@link #operations()} instead
	 * @param modules
	 * @throws CoreException
	 */
	public ICloudFoundryOperation getRestartOperation(IModule[] modules) throws CoreException {
		return operations().applicationDeployment(modules, ApplicationAction.RESTART);
	}

	/**
	 * Updates an the number of application instances. Does not restart the
	 * application if the application is already running. The CF server does
	 * allow instance scaling to occur while the application is running.
	 * @deprecated Use {@link #operations()} instead.
	 * @param module representing the application. must not be null or empty
	 * @param instanceCount must be 1 or higher.
	 * @param monitor
	 * @throws CoreException if error occurred during or after instances are
	 * updated.
	 */
	public void updateApplicationInstances(final CloudFoundryApplicationModule module, final int instanceCount,
			IProgressMonitor monitor) throws CoreException {
		operations().instancesUpdate(module, instanceCount).run(monitor);
	}

	/**
	 * Updates an the number of application instances in the Cloud space, but
	 * does not update the associated application module. Does not restart the
	 * application if the application is already running. The CF server does
	 * allow instance scaling to occur while the application is running.
	 * @param module representing the application. must not be null or empty
	 * @param instanceCount must be 1 or higher.
	 * @param monitor
	 * @throws CoreException if error occurred during or after instances are
	 * updated.
	 */
	void updateApplicationInstances(final String appName, final int instanceCount, IProgressMonitor monitor)
			throws CoreException {
		getBehaviourClient(monitor).updateApplicationInstances(appName, instanceCount, monitor);
	}

	public void updatePassword(final String newPassword, IProgressMonitor monitor) throws CoreException {
		getBehaviourClient(monitor).updatePassword(newPassword, monitor);
	}

	/**
	 * Updates an application's memory. Does not restart an application if the
	 * application is currently running. The CF server does allow memory scaling
	 * to occur while the application is running.
	 * @param module must not be null or empty
	 * @param memory must be above zero.
	 * @param monitor
	 * @deprecated use {@link #operations()} instead
	 * @throws CoreException if error occurred during or after memory is scaled.
	 * Exception does not always mean that the memory changes did not take
	 * effect. Memory could have changed, but some post operation like
	 * refreshing may have failed.
	 */
	public void updateApplicationMemory(final CloudFoundryApplicationModule module, final int memory,
			IProgressMonitor monitor) throws CoreException {
		operations().memoryUpdate(module, memory).run(monitor);
	}

	/**
	 * @deprecated use {@link #operations()} instead
	 * @param appName
	 * @param uris
	 * @param monitor
	 * @throws CoreException
	 */
	public void updateApplicationUrls(final String appName, final List<String> uris, IProgressMonitor monitor)
			throws CoreException {
		operations().mappedUrlsUpdate(appName, uris).run(monitor);
	}

	/**
	 * deprecated Use {@link #operations()} instead.
	 */
	public void updateServices(String appName, List<String> services, IProgressMonitor monitor) throws CoreException {
		CloudFoundryApplicationModule appModule = getCloudFoundryServer().getExistingCloudModule(appName);
		operations().bindServices(appModule, services);
	}

	public void refreshApplicationBoundServices(CloudFoundryApplicationModule appModule, IProgressMonitor monitor)
			throws CoreException {
		DeploymentInfoWorkingCopy copy = appModule.resolveDeploymentInfoWorkingCopy(monitor);
		List<CFServiceInstance> boundServices = copy.getServices();
		if (boundServices != null && !boundServices.isEmpty()) {

			List<CFServiceInstance> allServices = getServices(monitor);
			if (allServices != null) {
				Map<String, CFServiceInstance> existingAsMap = new HashMap<String, CFServiceInstance>();

				for (CFServiceInstance existingServices : allServices) {
					existingAsMap.put(existingServices.getName(), existingServices);
				}

				List<CFServiceInstance> updatedServices = new ArrayList<CFServiceInstance>();

				for (CFServiceInstance boundService : boundServices) {
					CFServiceInstance updatedService = existingAsMap.get(boundService.getName());
					// Check if there is an updated mapping to an actual Cloud
					// Service or retain the old one.
					if (updatedService != null) {
						updatedServices.add(updatedService);
					}
					else {
						updatedServices.add(boundService);
					}
				}

				copy.setServices(updatedServices);
				copy.save();
			}

		}
	}

	public void register(final String email, final String password, IProgressMonitor monitor) throws CoreException {
		getBehaviourClient(monitor).register(email, password, monitor);
	}

	/**
	 * Called by getClient(...) to prompt the user and reacquire token with
	 * passcode, if an exception occurs while connecting with SSO
	 */
	private void reestablishSsoSessionIfNeeded(CloudFoundryException e, CloudFoundryServer cloudServer, IProgressMonitor monitor)
			throws CoreException {
		// Status Code: 401 / Description = Invalid Auth Token, or;
		// Status Code: 403 / Description: Access token denied.
		if (cloudServer.isSso()
				&& (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN)) {
			boolean result = CloudFoundryPlugin.getCallback().ssoLoginUserPrompt(cloudServer);
			if (behaviourClient != null) {
				// Success: another thread has established the client.
				return;
			}

			if (result) {
				// Client is null but the login did succeed, so recreate w/ new
				// token stored in server
				CFCloudCredentials credentials = CloudUtil.createCFSsoCredentials(cloudServer.getPasscode(),
						cloudServer.getToken());
				behaviourClient = CFClientProviderRegistry.INSTANCE.createBehaviourClient(cloudServer.getServer(),
						credentials, getCloudInfo(), monitor);
				return;
			}
		}

		// Otherwise, rethrow the exception
		throw e;

	}

	private final ReentrantLock clientLock = new ReentrantLock();

	private final ReentrantLock cloudInfoLock = new ReentrantLock();
	private CFInfo cachedCloudInfo;

	/**
	 * Gets the active client used by the behaviour for server operations.
	 * However, clients are created lazily, and invoking it multipe times does
	 * not recreate the client, as only one client is created per lifecycle of
	 * the server behaviour (but not necessarily per connection session, as the
	 * server behaviour may be created and disposed multiple times by the WST
	 * framework). To use the server-stored credentials, pass null credentials.
	 * <p/>
	 * This API is not suitable to changing credentials. User appropriate API
	 * for the latter like {@link #updatePassword(String, IProgressMonitor)}
	 */
	protected CFBehaviourClient getOrCreateBehaviourClient(boolean ignoreLock, IProgressMonitor monitor)
			throws CoreException {
		
		boolean weOwnLock = false;
		
		try {
			
			// The lock 'clientLock' exists to ensure that only one attempt to create a client occurs at a time.
			// However: attempts by the user to log-in w/ SSO should skip to the front of the line, as it enables all other requests to complete.
			// Thus the SSO log-in dialog passes true here, all others (including non-SSO) must pass false.
			if(!ignoreLock) {
				clientLock.lock();
				weOwnLock = true;
			}
			
			if (behaviourClient == null) {
				CloudFoundryServer cloudServer = getCloudFoundryServer();
	
				if (!cloudServer.hasCloudSpace()) {
					throw CloudErrorUtil.toCoreException(
							NLS.bind(Messages.ERROR_FAILED_CLIENT_CREATION_NO_SPACE, cloudServer.getServerId()));
				}
					
				CFCloudCredentials credentials;
				if(getCloudFoundryServer().isSso()) {
					try {
						credentials = CloudUtil.createCFSsoCredentials(cloudServer.getPasscode(), cloudServer.getToken());
						behaviourClient = CFClientProviderRegistry.INSTANCE.createBehaviourClient(cloudServer.getServer(), credentials, getCloudInfo(), monitor);
					} catch(CloudFoundryException e) {
						// On auth fail, rerequest from user if the exception indicated a token issue
						reestablishSsoSessionIfNeeded(e, cloudServer, monitor);
					}
					
				} else {
					String userName = cloudServer.getUsername();
					String password = cloudServer.getPassword();
					credentials = new CFCredentials(userName, password);
					behaviourClient = CFClientProviderRegistry.INSTANCE.createBehaviourClient(cloudServer.getServer(), credentials, getCloudInfo(), monitor);
				}
			}
			return behaviourClient;
			
		} finally { // end try
			if(weOwnLock) {
				clientLock.unlock();
			}
		}
	}

	/**
	 * Returns v1 client. Deprecated.
	 * 
	 * <b/>
	 * Use {@link #getBehaviourClient()} instead
	 * @param monitor
	 * @return
	 * @throws CoreException
	 */
	@Deprecated
	public CloudFoundryOperations getClient(IProgressMonitor monitor) throws CoreException {
		return getBehaviourClient(monitor).getV1Client().getClient();
	}

	public CFBehaviourClient getBehaviourClient(IProgressMonitor monitor) throws CoreException {
		return getOrCreateBehaviourClient(false, monitor);
	}

	@Override
	protected void initialize(IProgressMonitor monitor) {
		super.initialize(monitor);

		CFRebelServerIntegration integration = CloudFoundryPlugin.getCallback().getJRebelServerIntegration();
		if (integration != null) {
			integration.register();
		}
		getServer().addServerListener(serverListener, ServerEvent.SERVER_CHANGE);

		try {
			// This code will just throw an exception for an sso server
			if (getCloudFoundryServer().isSso() && getCloudFoundryServer().getToken() == null) {
				return;
			}

			getApplicationUrlLookup().refreshDomains(monitor);

			// Important: Must perform a refresh operation
			// as any operation that calls the CF client first
			// performs a server connection and sets server state.
			// The server connection is indirectly performed by this
			// first refresh call.
			asyncUpdateAll();

			ServerEventHandler.getDefault().fireServerEvent(
					new CloudServerEvent(getCloudFoundryServer(), CloudServerEvent.EVENT_SERVER_CONNECTED));
		}
		catch (CoreException e) {
			CloudFoundryPlugin.logError(e);
		}
	}

	/**
	 * If found, will attempt to publish module with the given name, and it
	 * assumes it is being added for the first time. NOTE: This method is only
	 * intended to bypass the WST framework in cases not supported by WST (for
	 * example, drag/drop an application to a non-WST view or UI control).
	 * Otherwise, WST-based deployments of applications (e.g. Run on Server,
	 * drag/drop to Servers view) should rely on the framework to invoke the
	 * appropriate publish method in the behaviour.
	 * 
	 * @see #publishModule(int, int, IModule[], IProgressMonitor)
	 * @param moduleName
	 * @param monitor
	 * @return status of publish
	 */
	public IStatus publishAdd(String moduleName, IProgressMonitor monitor) {
		List<IModule[]> allModules = getAllModules();
		try {
			for (IModule[] module : allModules) {
				if (module[0].getName().equals(moduleName)) {
					operations().applicationDeployment(module, ApplicationAction.PUSH).run(monitor);
					return Status.OK_STATUS;
				}
			}
		}
		catch (CoreException ce) {
			handlePublishError(ce);
			return ce.getStatus();
		}
		return CloudFoundryPlugin.getErrorStatus("Internal error: no module with name : " + moduleName //$NON-NLS-1$
				+ " found to publish. Refresh or clean the server and try again.");//$NON-NLS-1$
	}

	/**
	 * Judges whether there is a <code>CloudFoundryApplicationModule</code> with
	 * the given name in current server or not.
	 * 
	 * @param moduleName the module name to be checked
	 * @return true if there is a <code>CloudFoundryApplicationModule</code>
	 * with the given name in current server, false otherwise
	 */
	public boolean existCloudApplicationModule(String moduleName) {
		List<IModule[]> allModules = getAllModules();
		for (IModule[] modules : allModules) {
			if (modules[0] instanceof CloudFoundryApplicationModule && modules[0].getName().equals(moduleName)) {
				return true;
			}
		}
		return false;
	}

	protected void handlePublishError(CoreException e) {
		IStatus errorStatus = CloudFoundryPlugin
				.getErrorStatus(NLS.bind(Messages.ERROR_FAILED_TO_PUSH_APP, e.getMessage()));

		CloudFoundryPlugin.log(errorStatus);
	}

	/** Used by publishModules(...) to sort parents and children of those parents, while preserving the module[] list and deltakind*/
	private static class ModuleAndDeltaKind {
		final IModule[] modules;
		final int deltaKind2;
		
		/** Index into the original List<IModule[]> provided to publishModules. */
		final int index; 
		
		public ModuleAndDeltaKind(IModule[] modules, int deltaKind2, int index) {
			this.modules = modules;
			this.deltaKind2 = deltaKind2;
			this.index = index;
		}
	}

	@Override
	protected void publishModules(int kind, List/*<IModule[]>*/ modules, List/*<Integer>*/ deltaKind2, MultiStatus multi,
			IProgressMonitor monitor) {
		
		// NOTE: this is a workaround to avoid server-wide publish when removing
		// a module (i.e., deleting an application) as
		// well as publishing
		// an application for the first time. The issue: If there
		// are other
		// modules aside from the module being added or removed, that also have
		// changes, those modules
		// will be republished. There is a WST preference (
		// ServerPreferences#setAutoPublishing) that prevent modules from being
		// published automatically on
		// add/delete, but since this is a global preference, and it
		// affects all WST server contributions, not just Cloud Foundry.
		// Therefore,
		// preventing server-wide publish for just Cloud Foundry servers by
		// setting this preference is not advisable. Until WST supports per-app
		// add/delete without triggering a server publish, this seems to be a
		// suitable
		// workaround.
		
		// Whether or not we have determined if at least one root (parent) module was added or removed, and thus will be deleted from CF

		if ( modules != null && deltaKind2 != null) {

			boolean parentAddedOrRemoved = false;

			List<ModuleAndDeltaKind> topLevelParents = new ArrayList<ModuleAndDeltaKind>();

			// Gather top level parents into 'topLevelParents'
			for(int x = 0; x < modules.size(); x++) {

				if (monitor.isCanceled()) {
					return;
				}

				IModule[] module = (IModule[]) modules.get(x);

				if (module.length != 1) {
					continue;
				}
				
				if (shouldIgnorePublishRequest(module[module.length - 1])) {
					continue;
				}
				
				int knd = (int)deltaKind2.get(x); 

				if (ServerBehaviourDelegate.ADDED == knd || ServerBehaviourDelegate.REMOVED == knd) {
					parentAddedOrRemoved = true;
				}
				
				topLevelParents.add(new ModuleAndDeltaKind(module, (int)deltaKind2.get(x), x));
			}

			Map<Integer /* index of parent in 'modules' param */, List<ModuleAndDeltaKind> /*all children, grandchildren, etc of parent*/> transitiveChildren = new HashMap<Integer, List<ModuleAndDeltaKind>>();

			if(parentAddedOrRemoved) {
			
				// Gather children into childList and transitiveChildren
				for(int childIndex = 0; childIndex < modules.size(); childIndex++) {
	
					if (monitor.isCanceled()) {
						return;
					}
	
					IModule[] childModule = (IModule[]) modules.get(childIndex);
						
					
					// if not a top level parent...
					if(childModule.length > 1) { 

						if (shouldIgnorePublishRequest(childModule[childModule.length - 1])) {
							continue;
						}
						
						for(int parentIndex = 0; parentIndex < topLevelParents.size(); parentIndex++)  {
							// For each top level parent...
							ModuleAndDeltaKind parentModuleAndDelta = topLevelParents.get(parentIndex);
							if(childModule[0].getId().equals(parentModuleAndDelta.modules[0].getId())) {
								// If the current childModule has the parent, then add it to childList and transitiveChildren
								List<ModuleAndDeltaKind> childList = transitiveChildren.get((Integer)parentModuleAndDelta.index);
								if(childList == null) {
									childList = new ArrayList<ModuleAndDeltaKind>();
									transitiveChildren.put((Integer)parentModuleAndDelta.index,childList);
								}
								childList.add(new ModuleAndDeltaKind(childModule, (int)deltaKind2.get(childIndex), childIndex ));							
							}
						}
					}
				}

				// We know there exists at least one parent which has been added or removed, so only include those parents (and their children, regardless 
				// of deltaKind2 value) that have been added/removed.
				List<IModule[]> newModulesList = new ArrayList<IModule[]>();
				List<Integer> newDeltaKind2List = new ArrayList<Integer>();
				
				for(ModuleAndDeltaKind parent : topLevelParents) {
					
					// For each top level parent that was directly ADDED or REMOVED
					if(ServerBehaviourDelegate.ADDED == parent.deltaKind2 || ServerBehaviourDelegate.REMOVED == parent.deltaKind2) {
						
						// Add parent module and deltaKind
						newModulesList.add(parent.modules);
						newDeltaKind2List.add(parent.deltaKind2);
						
						// Get all the children, and add their module and deltaKind
						List<ModuleAndDeltaKind> childrenOfParent = transitiveChildren.get(parent.index);
						
						if(childrenOfParent != null) {
							for(ModuleAndDeltaKind child : childrenOfParent) {
								
								newModulesList.add(child.modules);
								newDeltaKind2List.add(child.deltaKind2);
							}
						}
						
					}
					
				}
				
				// Replace the existing module list+delataKind parameters, with our updated module list+deltaKind parameters
				// that only include parents that were ADDED/REMOVED (and all their children). 
				if(newModulesList.size() > 0 && newDeltaKind2List.size() > 0) {
					modules = newModulesList;
					deltaKind2 = newDeltaKind2List;
				}
				
			} // end if-parentsAddOrRemoved			
			
		}

		super.publishModules(kind, modules, deltaKind2, multi, monitor);

	}

	@Override
	protected void publishModule(int kind, int deltaKind, IModule[] module, IProgressMonitor monitor)
			throws CoreException {
	
		// Log publish module parameters
		CloudFoundryPlugin.logInfo(convertPublishModuleToString(deltaKind, module));
		
		super.publishModule(kind, deltaKind, module, monitor);

		try {
			// If the delta indicates that the module has been removed, remove
			// it
			// from the server.
			// Note that although the "module" parameter is of IModule[] type,
			// documentation
			// (and the name of the parameter) indicates that it is always one
			// module
			if (deltaKind == REMOVED && module.length == 1) {
				final CloudFoundryServer cloudServer = getCloudFoundryServer();
				// Get the existing cloud module to avoid recreating the one that was just deleted.
				final CloudFoundryApplicationModule cloudModule = cloudServer.getExistingCloudModule(module[0]);
				if (cloudModule != null && cloudModule.getApplication() != null) {
					getBehaviourClient(monitor).deleteApplication(cloudModule.getDeployedApplicationName(), monitor);
				}
			}
			else if (!module[0].isExternal()) {
				// These operations must ONLY be performed on NON-EXTERNAL
				// applications (apps with associated accessible workspace
				// projects).
				// Do not perform any updates or restarts on non-workspace
				// (external) apps, as some spaces may contain long-running
				// applications that
				// should not be restarted.
				int publishState = getServer().getModulePublishState(module);

				ICloudFoundryOperation op = null;
				
				final CloudFoundryServer cloudServer = getCloudFoundryServer();
				
				// Get the existing cloud module to avoid recreating the one that was just deleted.
				final CloudFoundryApplicationModule cloudModule = cloudServer.getExistingCloudModule(module[0]);
				if (cloudModule != null && cloudModule.isDeployed()) {
					
					if(deltaKind != ServerBehaviourDelegate.NO_CHANGE || isChildModuleChanged(module, monitor) ) {
						// Only update if the child module changed, or if the delta is added/changed
						op = operations().applicationDeployment(module, ApplicationAction.UPDATE_RESTART);						
					}
					
					
				} else if (deltaKind == ServerBehaviourDelegate.ADDED || publishState == IServer.PUBLISH_STATE_UNKNOWN) {
					// Application has not been published, so do a full
					// publish
					op = operations().applicationDeployment(module, ApplicationAction.PUSH);
				}
				else if (deltaKind == ServerBehaviourDelegate.CHANGED || deltaKind == ServerBehaviourDelegate.REMOVED) {
					op = operations().applicationDeployment(module, ApplicationAction.UPDATE_RESTART);
				}
				// Republish the root module if any of the child module requires
				// republish
				else if (isChildModuleChanged(module, monitor)) {
					op = operations().applicationDeployment(module, ApplicationAction.UPDATE_RESTART);
				}

				// NOTE: No need to run this as a separate Job, as publish
				// operations
				// are already run in a PublishJob. To better integrate with
				// WST, ensure publish operation
				// is run to completion in the PublishJob, unless launching
				// asynch events to notify other components while the main
				// publish operation is being run (e.g refresh UI, etc..).
				if (op != null) {
					ApplicationOperation ao = (ApplicationOperation)op;

					// The value 1000 matches the value in ServerBehaviourDelegate.publishModule(...). 
					// ServerBehaviourDelegate.publishModule() passes us a monitor upon which beginTask(1000) has 
					// already been called.
					ao.run(monitor, 1000);
				}
			}
		}
		catch (CoreException e) {
			handlePublishError(e);
			throw e;
		}
	}

	private boolean isChildModuleChanged(IModule[] module, IProgressMonitor monitor) {
		if (module == null || module.length == 0) {
			return false;
		}

		IServer myserver = this.getServer();
		IModule[] childModules = myserver.getChildModules(module, monitor);

		if (childModules != null && childModules.length > 0) {
			// Compose the full structure of the child module
			IModule[] currentChild = new IModule[module.length + 1];
			for (int i = 0; i < module.length; i++) {
				currentChild[i] = module[i];
			}
			for (IModule child : childModules) {
				currentChild[module.length] = child;

				if (myserver.getModulePublishState(currentChild) != IServer.PUBLISH_STATE_NONE
						|| isChildModuleChanged(currentChild, monitor)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Retrieves the orgs and spaces for the current server instance.
	 * @param monitor
	 * @return
	 * @throws CoreException if it failed to retrieve the orgs and spaces.
	 * @deprecated
	 */
	public CloudOrgsAndSpaces getCloudSpaces(IProgressMonitor monitor) throws CoreException {
	
		return getBehaviourClient(monitor).getV1Client().getOrgsAndSpacesV1(monitor);

	}

	/**
	 * Retrieves the routes for the given domain name; will return early if
	 * cancelled, with an OperationCanceledException.
	 */
	@Deprecated
	public List<CloudRoute> getRoutes(final String domainName, IProgressMonitor monitor) throws CoreException {
		return getBehaviourClient(monitor).getV1Client().getRoutesV1(domainName, monitor);
	}

	@Deprecated
	public void deleteRoute(final List<CloudRoute> routes, IProgressMonitor monitor) throws CoreException {
		getBehaviourClient(monitor).getV1Client().deleteRouteV1(routes, monitor);
	}

	public void deleteRoute(final String host, final String domainName, IProgressMonitor monitor) throws CoreException {
		 getBehaviourClient(monitor).deleteRoute(host, domainName, monitor);
	}

	/**
	 * Attempt to reserve a route; returns true if the route could be reserved,
	 * or false otherwise. Note: This will return false if user already owns the
	 * route, or if the route is owned by another user. Will return early if
	 * cancelled, with an OperationCanceledException.
	 */
	public boolean reserveRouteIfAvailable(final String host, final String domainName, IProgressMonitor monitor)
			throws CoreException {
		return getBehaviourClient(monitor).reserveRouteIfAvailable(host, domainName, monitor);
	}

	/**
	 * Attempts to retrieve cloud spaces using the given set of credentials and
	 * server URL. This bypasses the session client in a Cloud Foundry server
	 * instance, if one exists for the given server URL, and therefore attempts
	 * to retrieve the cloud spaces with a disposable, temporary client that
	 * logs in with the given credentials.Therefore, if fetching orgs and spaces
	 * from an existing server instance, please use
	 * {@link CloudFoundryServerBehaviour#getCloudSpaces(IProgressMonitor)}.
	 * @param behaviourClient
	 * @param selfSigned true if connecting to a self-signing server. False
	 * otherwise
	 * @param monitor which performs client login checks, and basic error
	 * handling. False if spaces should be obtained directly from the client
	 * API.
	 * 
	 * @return resolved orgs and spaces for the given credential and server URL.
	 */
	public static CloudOrgsAndSpaces getCloudSpacesExternalClient(CloudFoundryServer cfServer, CloudCredentials credentials, final String url,
			boolean selfSigned, IProgressMonitor monitor) throws CoreException {		
		return getCloudSpacesExternalClient(cfServer, credentials, url, selfSigned, false, null, null, monitor);
	}
	
	public static CloudOrgsAndSpaces getCloudSpacesExternalClient(CloudFoundryServer cfServer, CloudCredentials credentials, final String url,
			boolean selfSigned, final boolean sso, final String passcode, String tokenValue, IProgressMonitor monitor) throws CoreException {
		return V1ClientProvider.getCloudSpaces(cfServer, credentials, url, selfSigned, sso, passcode, tokenValue, monitor);

	}



	@Deprecated
	public static void validate(final CloudFoundryServer cloudServer, final String location, String userName, String password, boolean selfSigned,
			boolean sso, String passcode, String tokenValue, IProgressMonitor monitor) throws CoreException {
		V1ClientProvider.createClient(cloudServer, location, /*space*/ null, selfSigned, userName, password, sso, tokenValue, passcode, monitor);
	}

	@Deprecated
	public static CloudFoundryOperations createExternalClientLogin(final CloudFoundryServer cloudServer, final String location, String userName,
			String password, boolean selfSigned, IProgressMonitor monitor) throws CoreException {
		return V1ClientProvider.createClient(cloudServer, location, /*space*/ null, selfSigned, userName, password, false,  /*tokenValue*/ null, /*passcode*/ null, monitor);
	}
	
	@Deprecated
	public static CloudFoundryOperations createExternalClientLogin(final CloudFoundryServer cloudServer, final String location, String userName,
			String password, boolean selfSigned, boolean sso, String passcode, String tokenValue, IProgressMonitor monitor) throws CoreException {
		return V1ClientProvider.createClient(cloudServer, location, /*space*/ null, selfSigned, userName, password, sso, tokenValue, passcode, monitor);
	}

	/**
	 * Resets publish state of the given modules to
	 * {@link IServer#PUBLISH_STATE_NONE}
	 * @param modules
	 */
	void resetPublishState(IModule[] modules) {
		setModulePublishState(modules, IServer.PUBLISH_STATE_NONE);
	}

	// public static class RequestFactory extends
	// CommonsClientHttpRequestFactory {
	//
	// private HttpClient client;
	//
	// /**
	// * For testing.
	// */
	// public static boolean proxyEnabled = true;
	//
	// public RequestFactory(HttpClient client) {
	// super(client);
	// this.client = client;
	// }
	//
	// public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod)
	// throws IOException {
	// IProxyData[] proxy =
	// CloudFoundryPlugin.getDefault().getProxyService().select(uri);
	// if (proxyEnabled && proxy != null && proxy.length > 0) {
	// client.getHostConfiguration().setProxy(proxy[0].getHost(),
	// proxy[0].getPort());
	// }else {
	// client.getHostConfiguration().setProxyHost(null);
	// }
	// return super.createRequest(uri, httpMethod);
	// }
	//
	// }

	protected boolean hasChildModules(IModule[] modules) {
		IWebModule webModule = CloudUtil.getWebModule(modules);
		return webModule != null && webModule.getModules() != null && webModule.getModules().length > 0;
	}

	/**
	 * 
	 * @param descriptor that contains the application information, and that
	 * also will be updated with an archive containing the application resources
	 * to be deployed to the Cloud Foundry Server
	 * @param cloudModule the Cloud Foundry wrapper around the application
	 * module to be pushed to the server
	 * @param modules list of WTP modules.
	 * @param server where app should be pushed to
	 * @param
	 * @param monitor
	 * @throws CoreException if failure occurred while generated an archive file
	 * containing the application's payload
	 */
	protected CFApplicationArchive generateApplicationArchiveFile(ApplicationDeploymentInfo deploymentInfo,
			CloudFoundryApplicationModule cloudModule, IModule[] modules, Server server, boolean incrementalPublish,
			IProgressMonitor monitor) throws CoreException {

		// Perform local operations like building an archive file
		// and payload for the application
		// resources prior to pushing it to the server.

		// If the module is not external (meaning that it is
		// mapped to a local, accessible workspace project),
		// create an
		// archive file containing changes to the
		// application's
		// resources. Use incremental publishing if
		// possible.

		IModuleResource[] resources = getResources(modules);

		CFApplicationArchive archive = ApplicationRegistry.getApplicationArchive(cloudModule.getLocalModule(),
				getCloudFoundryServer().getServer(), resources, monitor);

		// If no application archive was provided,then attempt an incremental
		// publish. Incremental publish is only supported for apps without child
		// modules.
		if (archive == null && incrementalPublish && !hasChildModules(modules)) {
			// Determine if an incremental publish
			// should
			// occur
			// For the time being support incremental
			// publish
			// only if the app does not have child
			// modules
			// To compute incremental deltas locally,
			// modules must be provided
			// Computes deltas locally before publishing
			// to
			// the server.
			// Potentially more efficient. Should be
			// used
			// only on incremental
			// builds

			archive = getIncrementalPublishArchive(deploymentInfo, modules);
		}
		return archive;

	}

	/**
	 * Note that consoles may be mapped to an application's deployment name. If
	 * during deployment, the application name has changed, then this may result
	 * in two separate consoles.
	 * 
	 * 
	 * @param appModule consoles are associated with a particular deployed
	 * application. This must not be null.
	 * @param message
	 * @param clearConsole true if console should be cleared. False, if message
	 * should be tailed to existing content in the console.
	 * @param runningOperation if it is a message related to an ongoing
	 * operation, which will append "..." to the message
	 * @throws CoreException
	 */
	protected void clearAndPrintlnConsole(CloudFoundryApplicationModule appModule, String message)
			throws CoreException {
		message += '\n';
		printToConsole(appModule, message, true, false);
	}

	protected void printlnToConsole(CloudFoundryApplicationModule appModule, String message) throws CoreException {
		message += '\n';
		printToConsole(appModule, message, false, false);
	}

	protected void printErrorlnToConsole(CloudFoundryApplicationModule appModule, String message) throws CoreException {
		message = NLS.bind(Messages.CONSOLE_ERROR_MESSAGE + '\n', message);
		printToConsole(appModule, message, false, true);
	}

	/**
	 * Note that consoles may be mapped to an application's deployment name. If
	 * during deployment, the application name has changed, then this may result
	 * in two separate consoles.
	 * 
	 */
	protected void printToConsole(CloudFoundryApplicationModule appModule, String message, boolean clearConsole,
			boolean isError) throws CoreException {
		CloudFoundryPlugin.getCallback().printToConsole(getCloudFoundryServer(), appModule, message, clearConsole,
				isError);
	}

	protected CFApplicationArchive getIncrementalPublishArchive(final ApplicationDeploymentInfo deploymentInfo,
			IModule[] modules) {
		IModuleResource[] allResources = getResources(modules);
		IModuleResourceDelta[] deltas = getPublishedResourceDelta(modules);
		List<IModuleResource> changedResources = getChangedResources(deltas);
		CFApplicationArchive moduleArchive = new CachingApplicationArchive(Arrays.asList(allResources),
				changedResources, modules[0], deploymentInfo.getDeploymentName());

		return moduleArchive;
	}

	/** Convert a call to publishModule(...) to a String, for debugging */
	private static String convertPublishModuleToString(int deltaKind, IModule[] module) { 
		try {
			String deltaKindStr;
			
			if(deltaKind == REMOVED) {
				deltaKindStr = "REMOVED";
			} else if(deltaKind == ADDED) {
				deltaKindStr = "ADDED";
			} else if(deltaKind == CHANGED) {
				deltaKindStr = "CHANGED";
			} else if(deltaKind == NO_CHANGE) {
				deltaKindStr = "NO_CHANGE";
			} else {
				deltaKindStr = "Unknown";
			}
			
			String moduleStr = "{ ";
			if(module != null) {
				for(int x = 0; x < module.length; x++) {
					IModule currModule = module[x];
					
					if(currModule == null) { continue; } 
					
					moduleStr += currModule.getName()+" ["+currModule.getId()+"/"+(currModule.getModuleType() != null ? currModule.getModuleType().getId() : "")  +"]";
					
					if(x+1 < module.length) {
						moduleStr += ", ";
					}
				}
			}
			moduleStr = moduleStr.trim() + "}";
			
			return "CloudFoundryServerBehaviour.publishModule(...): "+deltaKindStr +" "+moduleStr;
			
		} catch(Exception t) {
			// This method is for logging only; we should not throw exceptions to calling methods under any circumstances.
		}
		
		return "";
	}

	/**
	 * Keep track on all the publish operation to be completed
	 * <p/>
	 * NS: Keeping in case a similar job monitor is needed in the future.
	 * @author eyuen
	 */
	static class PublishJobMonitor extends JobChangeAdapter {

		private List<Job> jobLst = new ArrayList<Job>();

		void init() {
			// Clean all existing jobs
			synchronized (jobLst) {
				jobLst.clear();
			}
		}

		@Override
		public void done(IJobChangeEvent event) {
			super.done(event);
			synchronized (jobLst) {
				jobLst.remove(event.getJob());
			}
		}

		void monitorJob(Job curJob) {
			curJob.addJobChangeListener(this);
			synchronized (jobLst) {
				jobLst.add(curJob);
			}
		}

		boolean isAllJobCompleted() {
			return jobLst.size() == 0;
		}

		/**
		 * Wait for all job to be completed or the monitor is cancelled.
		 * @param monitor
		 */
		void waitForJobCompletion(IProgressMonitor monitor) {
			while ((monitor == null || !monitor.isCanceled()) && jobLst.size() > 0) {
				try {
					Thread.sleep(500);
				}
				catch (InterruptedException e) {
					// Do nothing
				}
			}
		}
	}

	@Override
	public boolean canRestartModule(IModule[] modules) {
		try {
			CloudFoundryServer cloudServer = getCloudFoundryServer();
			IServer server = cloudServer.getServerOriginal();

			// If module is started, we should return true, regardless of the
			// publish state (this is for example an unlinked project that is
			// available and running, just not bound to any project in the
			// workspace)
			int moduleState = server.getModuleState(modules);
			if (moduleState == IServer.STATE_STARTED) {
				return true;
			}

			int publishState = server.getModulePublishState(modules);
			// If state is unknown, then inform module(s) can't be
			// restarted (which will disable push/start context menu operations)
			if (publishState == IServer.PUBLISH_STATE_UNKNOWN) {
				return false;
			}
		}
		catch (CoreException ce) {
			CloudFoundryPlugin.logError(ce);
		}

		return super.canRestartModule(modules);
	}

	public ApplicationInstanceRunningTracker getApplicationInstanceRunningTracker(
			CloudFoundryApplicationModule appModule) throws CoreException {
		return new ApplicationInstanceRunningTracker(appModule, getCloudFoundryServer());
	}

	public List<String> getBuildpacks(IProgressMonitor monitor) throws CoreException {
		return getBehaviourClient(monitor).getBuildpacks(monitor);
	}

	public boolean supportsSsh() {
		try {
			return getCloudInfo().supportsSsh();
		}
		catch (CoreException e) {
			CloudFoundryPlugin.logError(e);
		}
		return false;
	}

	public CFInfo getCloudInfo() throws CoreException {
		// cache the info to avoid frequent network connection to Cloud Foundry.
		try {
			cloudInfoLock.lock();
			if (cachedCloudInfo == null) {
				CloudFoundryServer cloudServer = getCloudFoundryServer();
				cachedCloudInfo = new CFInfo(cloudServer.getUrl(), cloudServer.getProxyConfiguration(),
						cloudServer.isSelfSigned());
			}
			return cachedCloudInfo;
		} finally {
			cloudInfoLock.unlock();
		}
	}

	/**
	 * Asynchronously updates all modules and services with information from
	 * Cloud Foundry.
	 */
	public void asyncUpdateAll() {
		UpdateOperationsScheduler scheduler = getUpdateModulesScheduler();
		if (scheduler != null) {
			scheduler.updateAll();
		}
	}

	/**
	 * Asynchronously updates the given module IFF it is already deployed to
	 * Cloud Foundry.
	 * @param localModule
	 */
	public void asyncUpdateDeployedModule(IModule module) {
		UpdateOperationsScheduler scheduler = getUpdateModulesScheduler();
		if (scheduler != null) {
			scheduler.updateDeployedModule(module);
		}
	}

	/**
	 * This generates a different event specific to publish operations (as
	 * opposed just refresh operations). Callers should use this method when
	 * updating a module information after a publish operation instead of
	 * {@link #asyncUpdateModule(IModule)}
	 * @param module
	 */
	public void asyncUpdateModuleAfterPublish(IModule module) {
		UpdateOperationsScheduler scheduler = getUpdateModulesScheduler();
		if (scheduler != null) {
			scheduler.updateModuleAfterPublish(module);
		}
	}

	/**
	 * Asynchronously updates a module, whether it is deployed or not. If the
	 * module is not deployed, it may be removed from the server.
	 * @param module
	 */
	public void asyncUpdateModule(IModule module) {
		UpdateOperationsScheduler scheduler = getUpdateModulesScheduler();
		if (scheduler != null) {
			scheduler.updateModule(module);
		}
	}

	/**
	 * 
	 * @param monitor
	 * @return SSH support, or null if not supported
	 */
	public ISshClientSupport getSshClientSupport(IProgressMonitor monitor) throws CoreException {
		CFInfo cloudInfo = getCloudInfo();
		if (cloudInfo.getSshHost() != null && cloudInfo.getSshClientId() != null) {
			ISshClientSupport ssh = SshClientSupport.create(getClient(monitor),  cloudInfo,
					getCloudFoundryServer().getProxyConfiguration(), getCloudFoundryServer(),
					getCloudFoundryServer().isSelfSigned());
			return ssh;
		}

        return null;
	}

	public List<CloudLog> getRecentAppLogs(final String appName, IProgressMonitor monitor) throws CoreException {
		return getBehaviourClient(monitor).getRecentLogs(appName, monitor);
	}

	public CFStreamingLogToken startAppLogStreaming(final String appName, final CFApplicationLogListener listener,
			IProgressMonitor monitor) throws CoreException {
		return getBehaviourClient(monitor).streamLogs(appName, listener, monitor);
	}
}

/**
 * Requests may be wrapped using this class, such that if the user cancels the
 * monitor, the thread will automatically return.
 * 
 * Note: Since the BaseClientRequest itself does not check the monitor, the
 * BaseClientRequest may still be running even though the calling thread has
 * return. Care should be taken to consider this logic.
 */
class CancellableRequestThread<T> {

	private T result = null;

	private Throwable exceptionThrown = null;

	private boolean threadComplete = false;

	private final Object lock = new Object();

	private final IProgressMonitor monitor;

	private final BaseClientRequest<T> request;

	public CancellableRequestThread(BaseClientRequest<T> request, IProgressMonitor monitor) {
		this.request = request;
		this.monitor = monitor;
	}

	/** This is called by ThreadWrapper.run(...) */
	private void runInThread() {

		try {
			result = request.run(monitor);
		}
		catch (Exception e) {
			exceptionThrown = e;
		}
		finally {
			synchronized (lock) {
				threadComplete = true;
				lock.notify();
			}
		}

	}

	/**
	 * Starts the thread to invoke the request, and begins waiting for the
	 * thread to complete or be cancelled.
	 */
	public T runAndWaitForCompleteOrCancelled() {
		try {

			// Start the thread that runs the requst
			ThreadWrapper tw = new ThreadWrapper();
			tw.start();

			while (!monitor.isCanceled()) {

				synchronized (lock) {
					// Check for cancelled every 0.25 seconds.
					lock.wait(250);

					if (threadComplete) {
						break;
					}
				}
			}

			Throwable thr = getExceptionThrown();
			// Throw any caught exceptions
			if (thr != null) {
				if (thr instanceof RuntimeException) {
					// Throw unchecked exception
					throw (RuntimeException) thr;

				}
				else {
					// Convert checked to unchecked exception
					throw new RuntimeException(thr);
				}

			}

			// Check for cancelled
			if (!isThreadComplete() && getResult() == null) {
				throw new OperationCanceledException();
			}

			T result = getResult();

			return result;

		}
		catch (InterruptedException e) {
			throw new OperationCanceledException();
		}
	}

	public Throwable getExceptionThrown() {
		synchronized (lock) {
			return exceptionThrown;
		}
	}

	public boolean isThreadComplete() {
		synchronized (lock) {
			return threadComplete;
		}
	}

	public T getResult() {
		synchronized (lock) {
			return result;
		}
	}

	/**
	 * Simple thread that calls runInThread(...), to ensure that the
	 * BaseClientRequest may only be started by calling the
	 * runAndWaitForCompleteOrCancelled(...) method.
	 */
	private class ThreadWrapper extends Thread {

		private ThreadWrapper() {
			setDaemon(true);
			setName(CancellableRequestThread.class.getName());
		}

		@Override
		public void run() {
			runInThread();
		}
	}
}
