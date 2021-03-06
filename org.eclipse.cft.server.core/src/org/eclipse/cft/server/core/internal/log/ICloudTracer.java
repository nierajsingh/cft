/*******************************************************************************
 * Copyright (c) 2014 Pivotal Software, Inc. 
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
package org.eclipse.cft.server.core.internal.log;

import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.cloudfoundry.client.lib.RestLogEntry;

/**
 * Streams the trace log generated by the Cloud Foundry trace framework. For
 * example, a UI component may implement this interface and trace the log to a
 * console when the Cloud Foundry trace framework notifies it that there is a
 * new trace log. Alternately, another component can implement this interface
 * and trace to a log file.
 *
 */
public interface ICloudTracer {

	/**
	 * Trace a new log entry generated by the Cloud Foundry framework.
	 * Typically, this gets invoked by the framework when a new REST request is
	 * being processed in the {@link CloudFoundryOperations}.
	 * @param restLogEntry
	 */
	public void traceNewLogEntry(RestLogEntry restLogEntry);

}
