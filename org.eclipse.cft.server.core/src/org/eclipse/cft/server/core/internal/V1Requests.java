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
package org.eclipse.cft.server.core.internal;

import java.util.ArrayList;
import java.util.List;

import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.eclipse.cft.server.core.internal.client.AdditionalV1Operations;
import org.eclipse.cft.server.core.internal.client.ClientRequestFactory;
import org.eclipse.cft.server.core.internal.client.diego.DiegoRequestProvider;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.osgi.util.NLS;
import org.eclipse.wst.server.core.IServer;

/**
 * Internal use only.
 * 
 * 
 * Provides additional V1 support. Should not be called outside of the framework
 * as V1 CF Java client is being phased out of CFT.
 *
 */
@Deprecated
public class V1Requests {

	// Ordered list of providers with highest priority first
	List<V1RequestProvider> providers = new ArrayList<V1RequestProvider>();

	public static final V1Requests INSTANCE = new V1Requests();

	public V1Requests() {
		addProvider(new DiegoRequestProvider());
		addProvider(new V1RequestProvider());
	}

	protected void addProvider(V1RequestProvider target) {
		if (target != null && !providers.contains(target)) {
			providers.add(target);
		}
	}

	/**
	 * 
	 * @param server
	 * @return non-null request provider
	 * @throws CoreException if no provider found for the given cloud server
	 */
	protected V1RequestProvider getRequestProvider(IServer server) throws CoreException {
		// Fetch by server URL first
		V1RequestProvider provider = null;
		for (V1RequestProvider prvd : providers) {
			if (prvd.supports(server)) {
				provider = prvd;
				break;
			}
		}

		if (provider == null) {
			throw CloudErrorUtil.toCoreException(
					NLS.bind(Messages.CloudFoundryTargetManager_NO_TARGET_DEFINITION_FOUND, server.getId()));
		}
		return provider;
	}

	public ClientRequestFactory getRequestFactory(IServer server, CloudFoundryOperations v1Client, AdditionalV1Operations additionalV1Operations)
			throws CoreException {
		return getRequestProvider(server).createRequestFactory(server, v1Client, additionalV1Operations);
	}
}
