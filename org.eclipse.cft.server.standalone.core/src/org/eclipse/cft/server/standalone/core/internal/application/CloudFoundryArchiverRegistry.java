/*******************************************************************************
 * Copyright (c) 2015, 2016 IBM Corporation and others.
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
 *     IBM Corporation - initial API and implementation
 ********************************************************************************/
package org.eclipse.cft.server.standalone.core.internal.application;

import org.eclipse.cft.server.core.internal.CloudErrorUtil;
import org.eclipse.cft.server.core.internal.CloudFoundryServer;
import org.eclipse.cft.server.core.internal.application.ICloudFoundryArchiver;
import org.eclipse.cft.server.core.internal.client.CloudFoundryApplicationModule;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;

/**
 * This is deprecated. Archive registry will be moved out of standalone plugin
 * into core plugin after CFT 1.0.1
 */
@Deprecated
public class CloudFoundryArchiverRegistry {

	public static final CloudFoundryArchiverRegistry INSTANCE = new CloudFoundryArchiverRegistry();

	private CloudFoundryArchiverRegistry() {
		// Cannot be created from outside
	}

	public ICloudFoundryArchiver createArchiver(CloudFoundryApplicationModule appModule, CloudFoundryServer cloudServer)
			throws CoreException {
		final String ARCHIVER_DELEGATE = "org.eclipse.cft.server.standalone.core.archiverDelegate"; //$NON-NLS-1$
		final String ARCHIVER_ELEMENT = "archiver"; //$NON-NLS-1$
		final String CLASS_ATTR = "class"; //$NON-NLS-1$

		// At present it just picks the first archiver extension

		IExtensionPoint archiverExtnPoint = Platform.getExtensionRegistry().getExtensionPoint(ARCHIVER_DELEGATE);
		if (archiverExtnPoint != null) {
			for (IExtension extension : archiverExtnPoint.getExtensions()) {
				for (IConfigurationElement config : extension.getConfigurationElements()) {
					if (ARCHIVER_ELEMENT.equals(config.getName())) {
						ICloudFoundryArchiver archiver = (ICloudFoundryArchiver) config
								.createExecutableExtension(CLASS_ATTR);
						return archiver;
					}
				}
			}
		}

		throw CloudErrorUtil.toCoreException("Could not locate archivers"); //$NON-NLS-1$
	}

}
