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
import java.util.stream.Collectors;

import org.cloudfoundry.client.lib.StartingInfo;
import org.cloudfoundry.client.lib.domain.CloudDomain;

public class CFTypesFromV1 {

	public static CFStartingInfo from(StartingInfo info) {
		return new CFStartingInfo(info.getStagingFile());
	}

	public static StartingInfo from(CFStartingInfo info) {
		return new StartingInfo(info.getStagingFile());
	}

	public static List<CFCloudDomain> from(List<CloudDomain> domain) {
		return domain.stream().map(dm -> from(dm)).collect(Collectors.toList());
	}

	public static CFCloudDomain from(CloudDomain domain) {
		return new CFCloudDomain(domain.getName());
	}
}
