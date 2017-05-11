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
package org.eclipse.cft.server.client.v2.internal;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

public class Logger {
	public static IStatus getErrorStatus(String message) {
		return getStatus(message, IStatus.ERROR);
	}

	public static IStatus getStatus(String message, int type) {
		return new Status(type, V2ClientIntegrationPlugin.PLUGIN_ID, message);
	}

	public static IStatus getErrorStatus(Throwable t) {
		return new Status(IStatus.ERROR, V2ClientIntegrationPlugin.PLUGIN_ID, t.getMessage(), t);
	}

	public static void log(IStatus status) {
		V2ClientIntegrationPlugin plugin = V2ClientIntegrationPlugin.getDefault();
		if (plugin != null) {
			plugin.getLog().log(status);
		}
	}

	public static void log(Throwable t) {
		log(getErrorStatus(t));
	}
}
