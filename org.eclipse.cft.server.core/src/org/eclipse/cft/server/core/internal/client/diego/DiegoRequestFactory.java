/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal Software, Inc. 
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
package org.eclipse.cft.server.core.internal.client.diego;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.eclipse.cft.server.core.ISshClientSupport;
import org.eclipse.cft.server.core.internal.CloudErrorUtil;
import org.eclipse.cft.server.core.internal.CloudFoundryPlugin;
import org.eclipse.cft.server.core.internal.CloudFoundryServer;
import org.eclipse.cft.server.core.internal.Messages;
import org.eclipse.cft.server.core.internal.client.ClientRequest;
import org.eclipse.cft.server.core.internal.client.ClientRequestFactory;
import org.eclipse.cft.server.core.internal.client.CloudFoundryApplicationModule;
import org.eclipse.cft.server.core.internal.client.CloudServerCFClient;
import org.eclipse.cft.server.core.internal.client.V1ClientRequest;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.osgi.util.NLS;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class DiegoRequestFactory extends ClientRequestFactory {

	public DiegoRequestFactory(CloudFoundryServer cloudServer, CloudServerCFClient cfClient) {
		super(cloudServer, cfClient);
	}

	@Override
	public ClientRequest<CloudApplication> getCloudApplication(final String appName) throws CoreException {

		return new V1ClientRequest<CloudApplication>(cloudServer, 
				NLS.bind(Messages.CloudFoundryServerBehaviour_GET_APPLICATION, appName)) {
			@Override
			protected CloudApplication runV1Request(CloudFoundryOperations client, IProgressMonitor progress) throws CoreException {
				try {
					return client.getApplication(appName);
				}
				catch (Exception e) {
					// In some cases fetching app stats to retrieve running
					// instances throws 503 due to
					// CF backend error
					if (CloudErrorUtil.is503Error(e)) {
						return cfClient.getAdditionalV1Operations(progress).getBasicApplication(appName);
					}
					else {
						throw e;
					}
				}
			}
		};
	}

	@Override
	public ClientRequest<List<CloudApplication>> getApplications() throws CoreException {

		final String serverId = cloudServer.getServer().getId();

		final String label = NLS.bind(Messages.CloudFoundryServerBehaviour_GET_ALL_APPS, serverId);

		return new V1ClientRequest<List<CloudApplication>>(cloudServer, label) {
			@Override
			protected List<CloudApplication> runV1Request(CloudFoundryOperations client, IProgressMonitor progress)
					throws CoreException {

				try {
					return client.getApplications();
				}
				catch (Exception e) {
					// In some cases fetching app stats to retrieve running
					// instances throws 503 due to
					// CF backend error
					if (CloudErrorUtil.is503Error(e)) {
						return cfClient.getAdditionalV1Operations(progress).getBasicApplications();
					}
					else {
						throw e;
					}
				}
			}
		};
	}

	@Override
	public ClientRequest<?> stopApplication(final String message, final CloudFoundryApplicationModule cloudModule) {
		return new V1ClientRequest<Void>(cloudServer, message) {
			@Override
			protected Void runV1Request(CloudFoundryOperations client, IProgressMonitor progress) throws CoreException {
				try {
					client.stopApplication(cloudModule.getDeployedApplicationName());
				}
				catch (Exception e) {
					// In some cases fetching app stats to retrieve running
					// instances throws 503 due to
					// CF backend error
					if (CloudErrorUtil.is503Error(e)) {
						cfClient.getAdditionalV1Operations(progress)
								.stopApplication(cloudModule.getDeployedApplicationName());
					}
					else {
						throw e;
					}
				}

				return null;
			}
		};
	}

	@Override
	public ClientRequest<String> getFile(final CloudApplication app, final int instanceIndex, final String path,
			final boolean isDir) throws CoreException {


		// If ssh is not supported, try the default legacy file fetching
		if (!cfClient.supportsSsh()) {
			return super.getFile(app, instanceIndex, path, isDir);
		}

		String label = NLS.bind(Messages.CloudFoundryServerBehaviour_FETCHING_FILE, path, app.getName());
		return new V1ClientRequest<String>(cloudServer, label) {
			@Override
			protected String runV1Request(CloudFoundryOperations client, IProgressMonitor progress) throws CoreException {

				if (path == null) {
					return null;
				}
		
				ISshClientSupport ssh = behaviour.getSshClientSupport(progress);
				if (ssh == null) {
					return null;
				}
				
				Session session = ssh.connect(app.getName(), instanceIndex, cloudServer.getServer(), progress);

				String command = isDir ? "ls -p " + path //$NON-NLS-1$
						// Basic work-around to scp which doesn't appear to work
						// well. Returns empty content for existing files.
						: "cat " + path; //$NON-NLS-1$

				try {
					Channel channel = session.openChannel("exec"); //$NON-NLS-1$
					((ChannelExec) channel).setCommand(command);

					return getContent(channel);
				}
				catch (JSchException e) {
					throw CloudErrorUtil.toCoreException(e);
				}
				finally {
					session.disconnect();
				}
			}
		};
	}

	protected String getContent(Channel channel) throws CoreException {
		InputStream in = null;
		OutputStream outStream = null;
		try {
			in = channel.getInputStream();
			channel.connect();

			if (in != null) {

				ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();
				outStream = new BufferedOutputStream(byteArrayOut);
				byte[] buffer = new byte[4096];
				int bytesRead = -1;

				while ((bytesRead = in.read(buffer)) != -1) {
					outStream.write(buffer, 0, bytesRead);
				}
				outStream.flush();
				byteArrayOut.flush();

				return byteArrayOut.toString();
			}
		}
		catch (IOException e) {
			throw CloudErrorUtil.toCoreException(e);
		}
		catch (JSchException e) {
			throw CloudErrorUtil.toCoreException(e);
		}
		finally {
			channel.disconnect();
			try {
				if (in != null) {
					in.close();
				}
				if (outStream != null) {
					outStream.close();
				}
			}
			catch (IOException e) {
				// Don't prevent any operation from completing if streams don't
				// close. Just log error
				CloudFoundryPlugin.logError(e);
			}
		}
		return null;
	}

}
