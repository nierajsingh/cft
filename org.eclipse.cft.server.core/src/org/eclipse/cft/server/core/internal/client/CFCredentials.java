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

import org.eclipse.cft.server.core.internal.CloudErrorUtil;
import org.eclipse.cft.server.core.internal.CloudUtil;
import org.eclipse.cft.server.core.internal.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.springframework.security.oauth2.common.OAuth2AccessToken;

import com.fasterxml.jackson.core.JsonProcessingException;

public class CFCredentials implements CFCloudCredentials {

	private String userName;

	private String password;

	private OAuth2AccessToken token;
	
	private String passcode;

	public CFCredentials(String userName, String password) {
		this.userName = userName;
		this.password = password;
	}

	public CFCredentials(String passcode, OAuth2AccessToken token) {
		this.passcode = passcode;
		this.token = token;
	}
	
	public CFCredentials(String passcode) {
		this.passcode = passcode;
	}

	@Override
	public String getUser() {
		return this.userName;
	}

	@Override
	public String getPassword() {
		return this.password;
	}

	@Override
	public String getAuthTokenAsJson() throws CoreException {
		if (this.token != null) {
			try {
				return CloudUtil.getTokenAsJson(this.token);
			}
			catch (JsonProcessingException e) {
				throw CloudErrorUtil.toCoreException(e);
			}
		} else {
			return null;
		}
	}

	@Override
	public String getClientId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getClientSecret() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getProxyUser() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isProxyUserSet() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isRefreshable() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getPasscode() {
		return this.passcode;
	}

	@Override
	public boolean isPasscodeSet() {
		return !StringUtils.isEmpty(getPasscode());
	}

}
