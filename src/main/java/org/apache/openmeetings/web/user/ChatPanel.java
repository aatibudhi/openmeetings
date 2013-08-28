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
package org.apache.openmeetings.web.user;

import static org.apache.openmeetings.OpenmeetingsVariables.webAppRootKey;
import static org.apache.openmeetings.web.app.Application.getBean;
import static org.apache.openmeetings.web.app.WebSession.getDateFormat;
import static org.apache.openmeetings.web.app.WebSession.getUserId;

import java.util.Date;

import org.apache.openmeetings.data.chat.ChatDao;
import org.apache.openmeetings.data.user.dao.UsersDao;
import org.apache.openmeetings.persistence.beans.chat.ChatMessage;
import org.apache.openmeetings.web.common.UserPanel;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormSubmitBehavior;
import org.apache.wicket.ajax.json.JSONException;
import org.apache.wicket.ajax.json.JSONObject;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.protocol.ws.IWebSocketSettings;
import org.apache.wicket.protocol.ws.api.IWebSocketConnection;
import org.apache.wicket.protocol.ws.api.IWebSocketConnectionRegistry;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

import com.googlecode.wicket.jquery.ui.plugins.emoticons.EmoticonsBehavior;
import com.googlecode.wicket.jquery.ui.plugins.wysiwyg.WysiwygEditor;

public class ChatPanel extends UserPanel {
	private static final Logger log = Red5LoggerFactory.getLogger(ChatPanel.class, webAppRootKey);
	private static final long serialVersionUID = -9144707674886211557L;
	private static final String MESSAGE_AREA_ID = "messageArea";
	private IModel<String> messageModel = Model.of("");
	
	private JSONObject getMessage(ChatMessage m) throws JSONException {
		return new JSONObject()
			.put("type", "chat")
			.put("msg", new JSONObject()
				.put("id", m.getId())
				.put("message", m.getMessage())
				.put("sent", getDateFormat().format(m.getSent()))
			);
	}

	public ChatPanel(String id) {
		super(id);
		setOutputMarkupId(true);
		setMarkupId(id);

		//TODO script should be moved from the html to parameterized file!
		add(new Behavior() {
			private static final long serialVersionUID = -2205036360048419129L;

			@Override
			public void renderHead(Component component, IHeaderResponse response) {
				ChatDao dao = getBean(ChatDao.class);
				try {				
					StringBuilder sb = new StringBuilder();
					//FIXME limited count should be loaded with "earlier" link
					for (ChatMessage m : dao.get(0, 30)) {
						sb.append("addChatMessageInternal(").append(getMessage(m).toString()).append(");");
					}
					if (sb.length() > 0) {
						sb.append("$('#").append(MESSAGE_AREA_ID).append("').emoticonize();");
						response.render(OnDomReadyHeaderItem.forScript(sb.toString()));
					}
				} catch (JSONException e) {
					
				}
				super.renderHead(component, response);
			}
		});
		add(new EmoticonsBehavior("#" + MESSAGE_AREA_ID));
		add(new WebMarkupContainer("messages").setMarkupId(MESSAGE_AREA_ID));
		final Form<Void> f = new Form<Void>("sendForm");
		ChatToolbar toolbar = new ChatToolbar("toolbarContainer");
		f.add(toolbar);
		final WysiwygEditor chatMessage = new WysiwygEditor("chatMessage", messageModel, toolbar);
		f.add(chatMessage);
		f.add(new Button("send").add(new AjaxFormSubmitBehavior("onclick"){
			private static final long serialVersionUID = -3746739738826501331L;
			
			protected void onSubmit(AjaxRequestTarget target) {
				ChatDao dao = getBean(ChatDao.class);
				ChatMessage m = new ChatMessage();
				m.setMessage(messageModel.getObject());
				m.setSent(new Date());
				m.setFromUser(getBean(UsersDao.class).get(getUserId()));
				dao.update(m);
				IWebSocketConnectionRegistry reg = IWebSocketSettings.Holder.get(getApplication()).getConnectionRegistry();
				for (IWebSocketConnection c : reg.getConnections(getApplication())) {
					try {
						c.sendMessage(getMessage(m).toString());
					} catch(Exception e) {
						log.error("Error while sending message", e);
					}
				}
				messageModel = Model.of(""); //HACK need to be fixed in WysiwygEditor
				chatMessage.setDefaultModel(messageModel);
				target.add(f);
			};
		}));
		add(f.setOutputMarkupId(true));
	}
}
