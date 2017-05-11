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

import java.util.function.Function;

import org.eclipse.cft.server.core.internal.CFLoginHandler;
import org.eclipse.cft.server.core.internal.CloudFoundryServer;
import org.eclipse.core.runtime.IProgressMonitor;

public class CFClientRequestProvider {

	private final CFClient client;

	private final CloudFoundryServer cloudServer;

	private final CFLoginHandler loginHandler;

	public CFClientRequestProvider(CloudFoundryServer cloudServer, CFClient client, CFLoginHandler loginHandler) {
		this.client = client;
		this.cloudServer = cloudServer;
		this.loginHandler = loginHandler;
	}

	public <T> T runAsRequest(Function<CFClient, T> request, String label, IProgressMonitor monitor) throws Exception {
		return new CFServerRequest<T>(cloudServer, client, loginHandler, request, label).run(monitor);
	}

}
