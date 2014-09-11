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
package org.apache.openmeetings.web.room;

import static org.apache.openmeetings.web.app.Application.getBean;
import static org.apache.openmeetings.web.app.WebSession.getUserId;
import static org.apache.openmeetings.web.app.WebSession.getLanguage;

import java.util.Arrays;
import java.util.List;

import org.apache.openmeetings.db.dao.room.PollDao;
import org.apache.openmeetings.db.dao.room.RoomDao;
import org.apache.openmeetings.db.dao.user.UserDao;
import org.apache.openmeetings.db.entity.room.PollType;
import org.apache.openmeetings.db.entity.room.RoomPoll;
import org.apache.openmeetings.db.entity.user.User;
import org.apache.openmeetings.web.app.WebSession;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.RequiredTextField;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;

import com.googlecode.wicket.jquery.ui.widget.dialog.AbstractFormDialog;
import com.googlecode.wicket.jquery.ui.widget.dialog.DialogButton;

public class CreatePollDialog extends AbstractFormDialog<RoomPoll> {
	private static final long serialVersionUID = 1L;
	private final DialogButton create = new DialogButton(WebSession.getString(22));
	private final DialogButton cancel = new DialogButton(WebSession.getString(25));
	private final long roomId;
	private final PollForm form;

	public CreatePollDialog(String id, long roomId) {
		super(id, WebSession.getString(18), new CompoundPropertyModel<RoomPoll>(new RoomPoll()));
		this.roomId = roomId;
		add(form = new PollForm("form", getModel()));
	}

	public void updateModel(AjaxRequestTarget target) {
		RoomPoll p = new RoomPoll();
		User u = getBean(UserDao.class).get(getUserId());
		p.setCreator(u);
		p.setRoom(getBean(RoomDao.class).get(roomId));
		p.setType(getBean(PollDao.class).getPollTypes(getLanguage()).get(0));
		form.setModelObject(p);
		target.add(form);
	}
	
	@Override
	protected List<DialogButton> getButtons() {
		return Arrays.asList(create, cancel);
	}
	
	@Override
	protected DialogButton getSubmitButton() {
		return create;
	}

	@Override
	public PollForm getForm() {
		return form;
	}

	@Override
	protected void onError(AjaxRequestTarget target) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onSubmit(AjaxRequestTarget target) {
		// TODO Auto-generated method stub
		
	}

	private class PollForm extends Form<RoomPoll> {
		private static final long serialVersionUID = 1L;

		public PollForm(String id, IModel<RoomPoll> model) {
			super(id, model);
			add(new RequiredTextField<String>("name"));
			add(new TextArea<String>("question"));
			add(new DropDownChoice<PollType>("type", getBean(PollDao.class).getPollTypes(getLanguage()), new ChoiceRenderer<PollType>("label.fieldlanguagesvalue.value", "id")));
		}
	}
}
