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
package org.eclipse.cft.server.core.internal.client;

import java.util.List;

import org.eclipse.cft.server.core.CFServiceInstance;
import org.eclipse.cft.server.core.EnvironmentVariable;
import org.eclipse.cft.server.core.internal.ApplicationAction;
import org.eclipse.cft.server.core.internal.CloudErrorUtil;
import org.eclipse.cft.server.core.internal.CloudFoundryPlugin;
import org.eclipse.cft.server.core.internal.Messages;
import org.eclipse.cft.server.core.internal.ServerEventHandler;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.osgi.util.NLS;
import org.eclipse.wst.server.core.IModule;

/**
 * 
 * Creates Cloud operations defined by {@link ICloudFoundryOperation} for start,
 * stopping, publishing, scaling applications, as well as creating, deleting,
 * and binding services.
 * <p/>
 * {@link ICloudFoundryOperation} should be used for performing Cloud operations
 * that require firing server and module refresh events.
 */
public class CloudBehaviourOperations {

	public static String INTERNAL_ERROR_NO_WST_MODULE = "Internal Error: No WST IModule specified - Unable to deploy or start application"; //$NON-NLS-1$

	private final CloudFoundryServerBehaviour behaviour;

	public CloudBehaviourOperations(CloudFoundryServerBehaviour behaviour) {
		this.behaviour = behaviour;
	}

	/**
	 * Get operation to create a list of services
	 * @param services
	 * @param monitor
	 * @throws CoreException if operation was not created
	 */
	public ICloudFoundryOperation createServices(final CFServiceInstance[] services) throws CoreException {
		return new UpdateServicesOperation(
				(monitor) -> behaviour.getBehaviourClient(monitor).createServices(services, monitor), behaviour);
	}

	/**
	 * Gets an operation to delete Services
	 * @param services
	 * @throws CoreException if operation was not created.
	 */
	public ICloudFoundryOperation deleteServices(final List<String> services) throws CoreException {
		return new UpdateServicesOperation(
				(monitor) -> behaviour.getBehaviourClient(monitor).deleteServices(services, monitor), behaviour);
	}

	/**
	 * Gets an operation to update the number of application instances. The
	 * operation does not restart the application if the application is already
	 * running. The CF server does allow instance scaling to occur while the
	 * application is running.
	 * @param module representing the application. must not be null or empty
	 * @param instanceCount must be 1 or higher.
	 * @throws CoreException if operation was not created
	 */
	public ICloudFoundryOperation instancesUpdate(final CloudFoundryApplicationModule appModule,
			final int instanceCount) throws CoreException {

		return new ModulesOperation(behaviour, appModule.getLocalModule()) {

			@Override
			public void runOnVerifiedModule(IProgressMonitor monitor) throws CoreException {
				String appName = appModule.getDeployedApplicationName();

				// Update the instances in the Cloud space
				getBehaviour().updateApplicationInstances(appName, instanceCount, monitor);

				// Refresh the module with the new instances information
				getBehaviour().updateDeployedModule(appModule.getLocalModule(), monitor);

				// Fire a separate instances update event to notify listener who
				// are specifically listening
				// to instance changes that do not require a full application
				// refresh event.
				ServerEventHandler.getDefault().fireAppInstancesChanged(behaviour.getCloudFoundryServer(), getFirstModule());

				// Schedule another refresh application operation as instances
				// may take
				// time to be updated (the new instances may have to be
				// restarted in the Cloud Space)
				getBehaviour().asyncUpdateDeployedModule(getFirstModule());
			}

			@Override
			public String getOperationName() {
				return Messages.CloudBehaviourOperations_UPDATING_INSTANCES;
			}
		};
	}

	/**
	 * Gets an operation that updates an application's memory. The operation
	 * does not restart an application if the application is currently running.
	 * The CF server does allow memory scaling to occur while the application is
	 * running.
	 * @param module must not be null or empty
	 * @param memory must be above zero.
	 * @throws CoreException if operation was not created
	 */
	public ICloudFoundryOperation memoryUpdate(final CloudFoundryApplicationModule appModule, final int memory)
			throws CoreException {
		String opName = NLS.bind(Messages.CloudFoundryServerBehaviour_UPDATE_APP_MEMORY,
				appModule.getDeployedApplicationName());
		return new ApplicationUpdateOperation(
				(monitor) -> behaviour.getBehaviourClient(monitor).updateApplicationMemory(appModule, memory, monitor),
				behaviour, appModule, opName);
	}

	
	public ICloudFoundryOperation updateApplicationDiego(final CloudFoundryApplicationModule appModule, boolean diego)
			throws CoreException {
		String message;
		if (diego) {
			message = NLS.bind(Messages.CloudFoundryServerBehaviour_ENABLING_DIEGO,
					appModule.getDeployedApplicationName());
		}
		else {
			message = NLS.bind(Messages.CloudFoundryServerBehaviour_DISABLING_DIEGO,
					appModule.getDeployedApplicationName());
		}

		return new ApplicationUpdateOperation(
				(monitor) -> behaviour.getBehaviourClient(monitor).updateApplicationDiego(appModule, diego, monitor),
				behaviour, appModule, message);
	}


	public ICloudFoundryOperation updateApplicationEnableSsh(final CloudFoundryApplicationModule appModule, boolean enableSsh)
			throws CoreException {
		String message;
		if (enableSsh) {
			message = NLS.bind(Messages.CloudFoundryServerBehaviour_ENABLING_SSH,
					appModule.getDeployedApplicationName());
		}
		else {
			message = NLS.bind(Messages.CloudFoundryServerBehaviour_DISABLING_SSH,
					appModule.getDeployedApplicationName());
		}
		return new ApplicationUpdateOperation(
				(monitor) -> behaviour.getBehaviourClient(monitor).updateApplicationEnableSsh(appModule, enableSsh, monitor), behaviour,
				appModule, message);
	}

	
	/**
	 * Gets an operation to update the application's URL mapping.
	 * @throws CoreException if failed to create the operation
	 */
	public ICloudFoundryOperation mappedUrlsUpdate(final String appName, final List<String> urls) throws CoreException {

		final CloudFoundryApplicationModule appModule = behaviour.getCloudFoundryServer()
				.getExistingCloudModule(appName);
       
		if (appModule != null) {
			String opName = NLS.bind(Messages.CloudFoundryServerBehaviour_UPDATE_APP_URLS, appName);
			return new ApplicationUpdateOperation((monitor) -> behaviour.getBehaviourClient(monitor).updateAppRoutes(appName, urls, monitor),
					behaviour, appModule.getLocalModule(), opName);
		}
		else {
			throw CloudErrorUtil.toCoreException(
					"Expected an existing Cloud application module but found none. Unable to update application URLs"); //$NON-NLS-1$
		}
	}

	/**
	 * Gets an operation to update the service bindings of an application
	 * @throws CoreException if operation was not created
	 */
	public ICloudFoundryOperation bindServices(final CloudFoundryApplicationModule appModule,
			final List<String> services) throws CoreException {
		String opName = NLS.bind(Messages.CloudFoundryServerBehaviour_UPDATE_SERVICE_BINDING,
				appModule.getDeployedApplicationName());
		return new ApplicationUpdateOperation(
				(monitor) -> behaviour.getBehaviourClient(monitor)
						.updateServiceBindings(appModule.getDeployedApplicationName(), services, monitor),
				behaviour, appModule.getLocalModule(), opName);
	}

	/**
	 * Gets an operation that updates the application's environment variables.
	 * Note that the application needs to first exist in the server, and be in a
	 * state that will accept environment variable changes (either stopped, or
	 * running after staging has completed).
	 * 
	 * @throws CoreException if operation was not created
	 */
	public ICloudFoundryOperation environmentVariablesUpdate(IModule module, String appName,
			List<EnvironmentVariable> variables) throws CoreException {
		String opName = NLS.bind(Messages.CloudFoundryServerBehaviour_UPDATE_ENV_VARS, appName);

		return new ApplicationUpdateOperation(
				(monitor) -> behaviour.getBehaviourClient(monitor).updateEnvironmentVariables(appName, variables, monitor),
				behaviour, module, opName);
	}

	/**
	 * Returns an executable application operation based on the given Cloud
	 * Foundry application module and an application start mode (
	 * {@link ApplicationAction} ).
	 * <p/>
	 * Throws error if failure occurred while attempting to resolve an
	 * operation. If no operation is resolved and no errors occurred while
	 * attempting to resolve an operation, null is returned, meaning that no
	 * operation is currently defined for the given deployment mode.
	 * <p/>
	 * It does NOT execute the operation.
	 * @param application
	 * @param action
	 * @return resolved executable operation associated with the given
	 * deployment mode, or null if an operation could not be resolved.
	 * @throws CoreException
	 */
	public ICloudFoundryOperation applicationDeployment(CloudFoundryApplicationModule application,
			ApplicationAction action) throws CoreException {
		IModule[] modules = new IModule[] { application.getLocalModule() };

		return applicationDeployment(modules, action, true);
	}

	public ICloudFoundryOperation applicationDeployment(IModule[] modules, ApplicationAction action)
			throws CoreException {
		return applicationDeployment(modules, action, true);
	}

	/**
	 * Resolves an {@link ICloudFoundryOperation} that performs a start, stop,
	 * restart or push operation for the give modules and specified
	 * {@link ApplicationAction}.
	 * <p/>
	 * If no operation can be specified, throws {@link CoreException}
	 * @param modules
	 * @param action
	 * @return Non-null application operation.
	 * @throws CoreException if operation cannot be resolved.
	 */
	public ICloudFoundryOperation applicationDeployment(IModule[] modules, ApplicationAction action,
			boolean clearConsole) throws CoreException {

		if (modules == null || modules.length == 0) {
			throw CloudErrorUtil.toCoreException(INTERNAL_ERROR_NO_WST_MODULE);
		}
		ICloudFoundryOperation operation = null;
		// Set the deployment mode
		switch (action) {
		case START:
			boolean incrementalPublish = false;
			// A start operation that always performs a full publish
			operation = new StartOperation(behaviour, incrementalPublish, modules, clearConsole);
			break;
		case STOP:
			operation = new StopApplicationOperation(behaviour, modules);
			break;
		case RESTART:
			operation = new RestartOperation(behaviour, modules, clearConsole);
			break;
		case UPDATE_RESTART:
			// Check the full publish preference to determine if full or
			// incremental publish should be done when starting an application
			operation = new StartOperation(behaviour, CloudFoundryPlugin.getDefault().getIncrementalPublish(), modules,
					clearConsole);
			break;
		case PUSH:
			operation = new PushApplicationOperation(behaviour, modules, clearConsole);
			break;
		}

		if (operation == null) {
			throw CloudErrorUtil.toCoreException("Internal Error: Unable to resolve a Cloud application operation."); //$NON-NLS-1$
		}
		return operation;
	}

	/**
	 * Update all modules, services, and the instance info and stats for the
	 * given optional module.
	 * <p/>
	 * This may be a long running operation
	 * @return Non-null operation
	 */
	public ICloudFoundryOperation updateAll() {
		return new UpdateAllOperation(behaviour);
	}

	/**
	 * Updates module and notifies that module has been updated after publish.
	 * This generates a different event than
	 * {@link #updateDeployedModule(IModule)}, and should be used specifically
	 * after publish operations.
	 * @param module
	 * @return
	 */
	public ModulesOperation updateOnPublish(final IModule module) {
		return new ModulesOperation(behaviour, module) {

			@Override
			public void runOnVerifiedModule(IProgressMonitor monitor) throws CoreException {
				getBehaviour().updateDeployedModule(module, monitor);
				ServerEventHandler.getDefault().fireAppDeploymentChanged(behaviour.getCloudFoundryServer(), module);
			}

			@Override
			public String getOperationName() {
				return Messages.CloudBehaviourOperations_UPDATE_MODULE_AFTER_PUBLISH;
			}
		};
	}

	/**
	 * Updates a deployed module (deployed means that the module is associated
	 * with an existing Cloud application and deployment run state is known). If
	 * the module is not deployed, no update is performed. This method is used
	 * when the caller wants to guarantee that only Cloud information about the
	 * application is updated in the module IFF the module is already known to
	 * be deployed (e.g. Cloud application exists and the runstate of the
	 * application is known: started, stopped, etc..)
	 * @param module
	 * @return Non-null operation.
	 */
	public ModulesOperation updateDeployedModule(final IModule module) {
		return new UpdateDeployedOnlyOperation(behaviour, module);
	}

	/**
	 * Updates the given module in the Server regardless of whether it is
	 * deployed or not. Use this to update the Server in case module has been
	 * deleted. This is not as restrictive as
	 * {@link #updateDeployedModule(IModule)}, where update is only performed
	 * IFF the module is deployed.
	 * @param module
	 * @return Non-null operation.
	 */
	public ModulesOperation updateModule(final IModule module) {
		return new UpdateModuleOperation(behaviour, module);
	}

	public ICloudFoundryOperation deleteModules(IModule[] modules, final boolean deleteServices) {
		return new DeleteModulesOperation(behaviour, modules, deleteServices);
	}

}
