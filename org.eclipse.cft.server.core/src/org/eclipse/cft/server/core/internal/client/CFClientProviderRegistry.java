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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.cft.server.core.internal.CloudFoundryServer;
import org.eclipse.cft.server.core.internal.CloudServerUtil;
import org.eclipse.cft.server.core.internal.ProviderPriority;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.wst.server.core.IServer;

public class CFClientProviderRegistry {

	final private  String EXTENSION_POINT = "org.eclipse.cft.server.core.client"; //$NON-NLS-1$

	final private  String PROVIDER_ELEMENT = "clientProvider"; //$NON-NLS-1$
	
	final private static String PRIORITY_ATTR = "priority"; //$NON-NLS-1$

	final private  static String CLASS_ATTR = "class"; //$NON-NLS-1$

	// list of targets by priority
	private Map<ProviderPriority, List<CFClientProvider>> providersPerPriority = null;

	public static final CFClientProviderRegistry INSTANCE = new CFClientProviderRegistry();

	private V1ClientProvider v1ClientProvider = new V1ClientProvider();

	private CFClientProviderRegistry() {
	}

	public synchronized void load() throws CoreException {

		if (providersPerPriority == null) {
			providersPerPriority = new HashMap<>();

			IExtensionPoint extnPoint = Platform.getExtensionRegistry().getExtensionPoint(EXTENSION_POINT);
			if (extnPoint != null) {
				for (IExtension extension : extnPoint.getExtensions()) {
					for (IConfigurationElement config : extension.getConfigurationElements()) {
						if (PROVIDER_ELEMENT.equals(config.getName())) {
							CFClientProvider provider = (CFClientProvider) config.createExecutableExtension(CLASS_ATTR);
							if (provider != null) {						
								ProviderPriority priority = getPriority(config);
								List<CFClientProvider> prvlist = providersPerPriority.get(priority);
								if (prvlist == null) {
									prvlist = new ArrayList<>();
									providersPerPriority.put(priority, prvlist);
								}
								if (!prvlist.contains(provider)) {
									prvlist.add(provider);
								}
							}
						}
					}
				}
			}
		}

	}

	protected ProviderPriority getPriority(IConfigurationElement config) {
		if (config != null) {
			String val = config.getAttribute(PRIORITY_ATTR);
			return ProviderPriority.getPriority(val);
		} 
		return null;
	}

	protected CFClient createClientFromProviders(IServer server, CFCloudCredentials credentials, CFInfo info, IProgressMonitor monitor)
			throws CoreException {

		load();

		if (server != null) {
			CloudFoundryServer cloudServer = CloudServerUtil.getCloudServer(server);

			for (ProviderPriority priority : ProviderPriority.values()) {

				List<CFClientProvider> providers = providersPerPriority.get(priority);
				if (providers != null) {
					for (CFClientProvider prvd : providers) {
						if (prvd.supports(cloudServer.getUrl(), info)) {

							CFClient client = prvd.createClient(server, credentials,  monitor);
							if (client != null) {
								return client;
							}
						}
					}
				}
			}
		}
		return null;
	}

	public V1ClientProvider getV1ClientProvider()  {
		return INSTANCE.v1ClientProvider;
	}

	public  CFBehaviourClient createBehaviourClient(IServer server, CFCloudCredentials credentials, CFInfo cloudInfo, IProgressMonitor monitor) throws CoreException {
		V1CFClient v1Client= (V1CFClient)getV1ClientProvider().createClient(server, credentials, monitor);
		CFClient actualClient = createClientFromProviders(server, credentials, cloudInfo, monitor);
		return new CFBehaviourClient(v1Client, actualClient);
	}
}
