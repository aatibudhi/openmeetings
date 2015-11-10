/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") +  you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openmeetings.web.common.menu;

import org.apache.openmeetings.web.pages.MainPage;
import org.apache.wicket.ajax.AjaxRequestTarget;

public class RoomMenuItem extends MenuItem {
	private static final long serialVersionUID = 1L;

	public RoomMenuItem(String name) {
		this(name, null);
	}
	
	public RoomMenuItem(String name, String desc) {
		this(name, desc, true);
	}
	
	public RoomMenuItem(String name, String desc, boolean active) {
		super(name, desc);
		this.active = active;
	}
	
	public RoomMenuItem(String name, String desc, String cssClass) {
		super(name, desc);
		this.cssClass = cssClass;
	}
	
	@Override
	public void onClick(MainPage page, AjaxRequestTarget target) {
	}
}
