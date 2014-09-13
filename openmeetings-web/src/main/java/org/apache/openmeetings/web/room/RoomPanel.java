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

import static org.apache.openmeetings.util.OpenmeetingsVariables.CONFIG_APPLICATION_BASE_URL;
import static org.apache.openmeetings.util.OpenmeetingsVariables.CONFIG_REDIRECT_URL_FOR_EXTERNAL_KEY;
import static org.apache.openmeetings.util.OpenmeetingsVariables.webAppRootKey;
import static org.apache.openmeetings.web.app.Application.addUserToRoom;
import static org.apache.openmeetings.web.app.Application.getBean;
import static org.apache.openmeetings.web.app.Application.getRoomUsers;
import static org.apache.openmeetings.web.app.WebSession.getUserId;
import static org.apache.openmeetings.web.util.OmUrlFragment.ROOMS_PUBLIC;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.openmeetings.db.dao.basic.ConfigurationDao;
import org.apache.openmeetings.db.dao.file.FileExplorerItemDao;
import org.apache.openmeetings.db.dao.room.PollDao;
import org.apache.openmeetings.db.dao.room.RoomDao;
import org.apache.openmeetings.db.dao.user.UserDao;
import org.apache.openmeetings.db.entity.file.FileExplorerItem;
import org.apache.openmeetings.db.entity.file.FileItem;
import org.apache.openmeetings.db.entity.file.FileItem.Type;
import org.apache.openmeetings.db.entity.record.FlvRecording;
import org.apache.openmeetings.db.entity.room.Room;
import org.apache.openmeetings.db.entity.room.RoomModerator;
import org.apache.openmeetings.db.entity.user.Organisation;
import org.apache.openmeetings.db.entity.user.Organisation_Users;
import org.apache.openmeetings.db.entity.user.User;
import org.apache.openmeetings.db.entity.user.User.Right;
import org.apache.openmeetings.db.util.AuthLevelUtil;
import org.apache.openmeetings.web.app.Application;
import org.apache.openmeetings.web.app.Client;
import org.apache.openmeetings.web.app.WebSession;
import org.apache.openmeetings.web.common.BasePanel;
import org.apache.openmeetings.web.common.menu.MenuItem;
import org.apache.openmeetings.web.common.menu.MenuPanel;
import org.apache.openmeetings.web.common.menu.RoomMenuItem;
import org.apache.openmeetings.web.common.tree.FileItemTree;
import org.apache.openmeetings.web.common.tree.FileTreePanel;
import org.apache.openmeetings.web.common.tree.MyRecordingTreeProvider;
import org.apache.openmeetings.web.common.tree.PublicRecordingTreeProvider;
import org.apache.openmeetings.web.pages.MainPage;
import org.apache.openmeetings.web.room.message.RoomMessage;
import org.apache.wicket.ajax.AbstractDefaultAjaxBehavior;
import org.apache.wicket.ajax.AjaxEventBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.json.JSONArray;
import org.apache.wicket.ajax.json.JSONException;
import org.apache.wicket.ajax.json.JSONObject;
import org.apache.wicket.authroles.authorization.strategies.role.annotations.AuthorizeInstantiation;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.event.IEvent;
import org.apache.wicket.extensions.markup.html.repeater.tree.ITreeProvider;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.head.PriorityHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.protocol.ws.WebSocketSettings;
import org.apache.wicket.protocol.ws.api.IWebSocketConnection;
import org.apache.wicket.protocol.ws.api.WebSocketRequestHandler;
import org.apache.wicket.protocol.ws.api.event.WebSocketPushPayload;
import org.apache.wicket.protocol.ws.api.registry.IWebSocketConnectionRegistry;
import org.apache.wicket.protocol.ws.api.registry.PageIdKey;
import org.apache.wicket.protocol.ws.concurrent.Executor;
import org.apache.wicket.request.flow.RedirectToUrlException;
import org.apache.wicket.request.resource.JavaScriptResourceReference;
import org.apache.wicket.request.resource.ResourceReference;
import org.apache.wicket.util.string.Strings;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;
import org.wicketstuff.whiteboard.WhiteboardBehavior;

import com.googlecode.wicket.jquery.core.JQueryBehavior;
import com.googlecode.wicket.jquery.core.Options;
import com.googlecode.wicket.jquery.ui.form.button.Button;

@AuthorizeInstantiation("Room")
public class RoomPanel extends BasePanel {
	private static final long serialVersionUID = 1L;
	private static final Logger log = Red5LoggerFactory.getLogger(RoomPanel.class, webAppRootKey);
	private long roomId;
	private Client c;
	private final StartSharingEventBehavior startSharing;
	private final AbstractDefaultAjaxBehavior aab = new AbstractDefaultAjaxBehavior() {
		private static final long serialVersionUID = 1L;

		@Override
		protected void respond(AjaxRequestTarget target) {
			target.appendJavaScript("setHeight();");
			//TODO SID etc
			Room r = getBean(RoomDao.class).get(roomId);
			target.appendJavaScript(String.format("initVideo('%s', %s, %s, %s, %s);", "sid"
					, r.getId()
					, r.getIsAudioOnly()
					, 4L == r.getRoomtype().getRoomtypes_id()
					, getStringLabels(448, 449, 450, 451, 758, 447, 52, 53, 1429, 1430, 775, 452, 767, 764, 765, 918, 54, 761, 762).toString()
					));
			broadcast(roomId, new RoomMessage(roomId, c.getUserId(), RoomMessage.Type.roomEnter));
		}
	};
	private final InvitationDialog invite;
	private final CreatePollDialog createPoll;
	private final MenuPanel menuPanel;
	private final RoomMenuItem exitMenuItem = new RoomMenuItem(WebSession.getString(308), WebSession.getString(309), "room menu exit") {
		private static final long serialVersionUID = 1L;

		@Override
		public void onClick(MainPage page, AjaxRequestTarget target) {
			if (WebSession.getRights().contains(Right.Dashboard)) {
				page.updateContents(ROOMS_PUBLIC, target);
			} else {
				String url = getBean(ConfigurationDao.class).getConfValue(CONFIG_REDIRECT_URL_FOR_EXTERNAL_KEY, String.class, "");
				if (Strings.isEmpty(url)) {
					url = getBean(ConfigurationDao.class).getConfValue(CONFIG_APPLICATION_BASE_URL, String.class, "");
				}
				throw new RedirectToUrlException(url);
			}
		}
	};
	private final RoomMenuItem filesMenu = new RoomMenuItem(WebSession.getString(245), null, false);
	private final RoomMenuItem actionsMenu = new RoomMenuItem(WebSession.getString(635), null, false);
	private final RoomMenuItem inviteMenuItem = new RoomMenuItem(WebSession.getString(213), WebSession.getString(1489), false) {
		private static final long serialVersionUID = 1L;

		@Override
		public void onClick(MainPage page, AjaxRequestTarget target) {
			invite.updateModel(target);
			invite.open(target);
		}
	};
	private final RoomMenuItem shareMenuItem = new RoomMenuItem(WebSession.getString(239), WebSession.getString(1480), false) {
		private static final long serialVersionUID = 1L;

		@Override
		public void onClick(MainPage page, AjaxRequestTarget target) {
			startSharing.respond(target);
		}
	};
	private final RoomMenuItem applyModerMenuItem = new RoomMenuItem(WebSession.getString(784), WebSession.getString(1481), false);
	private final RoomMenuItem applyWbMenuItem = new RoomMenuItem(WebSession.getString(785), WebSession.getString(1492), false);
	private final RoomMenuItem applyAvMenuItem = new RoomMenuItem(WebSession.getString(786), WebSession.getString(1482), false);
	private final RoomMenuItem pollCreateMenuItem = new RoomMenuItem(WebSession.getString(24), WebSession.getString(1483), false) {
		private static final long serialVersionUID = 1L;

		@Override
		public void onClick(MainPage page, AjaxRequestTarget target) {
			createPoll.open(target);
		}
	};
	private final RoomMenuItem pollVoteMenuItem = new RoomMenuItem(WebSession.getString(42), WebSession.getString(1485), false);
	private final RoomMenuItem pollResultMenuItem = new RoomMenuItem(WebSession.getString(37), WebSession.getString(1484), false);
	private final RoomMenuItem sipDialerMenuItem = new RoomMenuItem(WebSession.getString(1447), WebSession.getString(1488), false);
	private final WebMarkupContainer userList = new WebMarkupContainer("userList");
	private final ListView<RoomClient> users;
	private final boolean showFiles;
	private final Button shareBtn = new Button("share");
	private final Button askBtn = new Button("ask");
	
	public RoomPanel(String id, long _roomId) {
		this(id, getBean(RoomDao.class).get(_roomId));
	}
	
	public RoomPanel(String id, Room r) {
		super(id);
		this.roomId = r.getId();
		add((menuPanel = new MenuPanel("roomMenu", getMenu())).setVisible(!r.getHideTopBar()));
		WebMarkupContainer wb = new WebMarkupContainer("whiteboard");
		add(wb.setOutputMarkupId(true));
		add(new WhiteboardBehavior("1", wb.getMarkupId(), null, null, null));
		add(aab, AttributeAppender.append("style", "height: 100%;"));
		showFiles = !r.getHideFilesExplorer();
		add(new WebMarkupContainer("flink").setVisible(showFiles));
		add(new WebMarkupContainer("ftab").add(new FileTreePanel("tree") {
			private static final long serialVersionUID = 1L;

			@Override
			public void updateSizes() {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void update(AjaxRequestTarget target, FileItem f) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void defineTrees() {
				FileExplorerItem f = new FileExplorerItem();
				f.setOwnerId(getUserId());
				selectedFile.setObject(f);
				treesView.add(selected = new FileItemTree<FileExplorerItem>(treesView.newChildId(), this, new FilesTreeProvider(null)));
				treesView.add(new FileItemTree<FileExplorerItem>(treesView.newChildId(), this, new FilesTreeProvider(roomId)));
				treesView.add(new FileItemTree<FlvRecording>(treesView.newChildId(), this, new MyRecordingTreeProvider()));
				treesView.add(new FileItemTree<FlvRecording>(treesView.newChildId(), this, new PublicRecordingTreeProvider(null, null)));
				for (Organisation_Users ou : getBean(UserDao.class).get(getUserId()).getOrganisation_users()) {
					Organisation o = ou.getOrganisation();
					treesView.add(new FileItemTree<FlvRecording>(treesView.newChildId(), this, new PublicRecordingTreeProvider(o.getId(), o.getName())));
				}
			}
			
			@Override
			public void createFolder(String name) {
				if (selectedFile.getObject() instanceof FlvRecording) {
					createRecordingFolder(name);
				} else {
					FileExplorerItem f = new FileExplorerItem();
					f.setFileName(name);
					f.setInsertedBy(getUserId());
					f.setInserted(new Date());
					f.setType(Type.Folder);;
					FileItem p = selectedFile.getObject();
					long parentId = p.getId();
					f.setParentItemId(Type.Folder == p.getType() && parentId > 0 ? parentId : null);
					f.setOwnerId(p.getOwnerId());
					f.setRoomId(p.getRoomId());
					getBean(FileExplorerItemDao.class).update(f);
				}
			}
		}).setVisible(showFiles));
		add(userList.add(users = new ListView<RoomClient>("user", getUsers()) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void populateItem(ListItem<RoomClient> item) {
				RoomClient rc = item.getModelObject();
				item.setMarkupId(String.format("user%s", rc.c.getUid()));
				item.add(new Label("name", rc.u.getFirstname() + " " + rc.u.getLastname()));
				if (c != null && rc.c.getUid().equals(c.getUid())) {
					item.add(AttributeAppender.append("class", "current"));
				}
			}
		}.setReuseItems(true)).setOutputMarkupId(true));
		add(new JQueryBehavior(".room.sidebar.left .tabs", "tabs", new Options("active", showFiles && r.isFilesOpened() ? "ftab" : "utab")) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void renderScript(JavaScriptHeaderItem script, IHeaderResponse response) {
				response.render(new PriorityHeaderItem(script));
			}
		});
		add(new Label("roomName", r.getName()));
		add(new Label("recording", "Recording started").setVisible(false)); //FIXME add/remove
		add(askBtn.setOutputMarkupPlaceholderTag(true).setVisible(false).add(new AttributeAppender("title", WebSession.getString(906))));
		add(startSharing = new StartSharingEventBehavior(roomId));
		add(shareBtn.add(new AjaxEventBehavior("click") {
			private static final long serialVersionUID = 1L;
			
			@Override
			protected void onEvent(AjaxRequestTarget target) {
				startSharing.respond(target);
			}
		}).setOutputMarkupPlaceholderTag(true).setVisible(false).add(new AttributeAppender("title", WebSession.getString(1480))));
		add(invite = new InvitationDialog("invite", roomId));
		add(createPoll = new CreatePollDialog("createPoll", roomId));
	}

	@Override
	public void onEvent(IEvent<?> event) {
		if (event.getPayload() instanceof WebSocketPushPayload) {
			WebSocketPushPayload wsEvent = (WebSocketPushPayload) event.getPayload();
			if (wsEvent.getMessage() instanceof RoomMessage) {
				RoomMessage m = (RoomMessage)wsEvent.getMessage();
				switch (m.getType()) {
					case pollCreated:
					case rightUpdated:
						updateUserMenuIcons(wsEvent.getHandler());
						break;
					case roomEnter:
						updateUserMenuIcons(wsEvent.getHandler());
					case roomExit:
						users.setList(getUsers());
						wsEvent.getHandler().add(userList);
						break;
					default:
						break;
				}
				//TODO check this menuPanel.modelChanged();
			}
		}
		super.onEvent(event);
	}
	
	private JSONArray getStringLabels(long... ids) {
		JSONArray arr = new JSONArray();
		try {
			for (long id : ids) {
				arr.put(new JSONObject().put("id", id).put("value", WebSession.getString(id)));
			}
		} catch (JSONException e) {
			log.error("", e);
		}
		return arr;
	}
	
	@Override
	protected void onBeforeRender() {
		super.onBeforeRender();
		c = addUserToRoom(roomId, getPage().getPageId());
		User u = getBean(UserDao.class).get(getUserId());
		//TODO do we need to check OrgModerationRights ????
		if (AuthLevelUtil.hasAdminLevel(u.getRights())) {
			c.getRights().add(Client.Right.moderator);
		} else {
			Room r = getBean(RoomDao.class).get(roomId);
			if (!r.isModerated() && 1 == getRoomUsers(roomId).size()) {
				c.getRights().add(Client.Right.moderator);
			} else if (r.isModerated()) {
				//TODO why do we need supermoderator ????
				for (RoomModerator rm : r.getModerators()) {
					if (getUserId() == rm.getUser().getId()) {
						c.getRights().add(Client.Right.moderator);
						break;
					}
				}
			}
		}
	}
	
	public static void broadcast(long roomId, final RoomMessage m) {
		WebSocketSettings settings = WebSocketSettings.Holder.get(Application.get());
		IWebSocketConnectionRegistry reg = settings.getConnectionRegistry();
		Executor executor = settings.getWebSocketPushMessageExecutor();
		for (Client c : getRoomUsers(roomId)) {
			try {
				final IWebSocketConnection wsConnection = reg.getConnection(Application.get(), c.getSessionId(), new PageIdKey(c.getPageId()));
				executor.run(new Runnable()
				{
					@Override
					public void run()
					{
						wsConnection.sendMessage(m);
					}
				});
			} catch (Exception e) {
				log.error("Error while sending message", e);
			}
		}
	}
	
	public static void sendRoom(long roomId, String msg) {
		IWebSocketConnectionRegistry reg = WebSocketSettings.Holder.get(Application.get()).getConnectionRegistry();
		for (Client c : getRoomUsers(roomId)) {
			try {
				reg.getConnection(Application.get(), c.getSessionId(), new PageIdKey(c.getPageId())).sendMessage(msg);
			} catch (Exception e) {
				log.error("Error while sending message", e);
			}
		}
	}
	
	private void updateUserMenuIcons(WebSocketRequestHandler handler) {
		boolean pollExists = getBean(PollDao.class).hasPoll(roomId);
		User u = getBean(UserDao.class).get(getUserId());
		boolean notExternalUser = u.getType() != User.Type.external && u.getType() != User.Type.contact;
		exitMenuItem.setActive(notExternalUser);//TODO check this
		filesMenu.setActive(showFiles);
		Room r = getBean(RoomDao.class).get(roomId);
		actionsMenu.setActive(!r.getHideActionsMenu());
		boolean moder = c.hasRight(Client.Right.moderator);
		inviteMenuItem.setActive(notExternalUser && moder);
		//TODO add check "sharing started"
		boolean shareVisible = 4 != r.getRoomtype().getRoomtypes_id() && moder; //FIXME hardcoded
		shareMenuItem.setActive(shareVisible);
		shareBtn.setVisible(shareMenuItem.isActive());
		applyModerMenuItem.setActive(!moder);
		applyWbMenuItem.setActive(!moder);
		applyAvMenuItem.setActive(!moder);
		pollCreateMenuItem.setActive(moder);
		pollVoteMenuItem.setActive(pollExists && notExternalUser);
		pollResultMenuItem.setActive(pollExists && notExternalUser);
		//TODO sip menus
		handler.add(menuPanel, askBtn.setVisible(!moder), shareBtn.setVisible(shareVisible));
	}
	
	private List<MenuItem> getMenu() {
		List<MenuItem> menu = new ArrayList<MenuItem>();
		exitMenuItem.setActive(false);
		menu.add(exitMenuItem);
		
		List<RoomMenuItem> fileItems = new ArrayList<RoomMenuItem>();
		fileItems.add(new RoomMenuItem(WebSession.getString(15), WebSession.getString(1479)));
		filesMenu.setChildren(fileItems);
		menu.add(filesMenu);
		
		List<RoomMenuItem> actionItems = new ArrayList<RoomMenuItem>();
		actionItems.add(inviteMenuItem);
		actionItems.add(shareMenuItem); //FIXME enable/disable
		actionItems.add(applyModerMenuItem); //FIXME enable/disable
		actionItems.add(applyWbMenuItem); //FIXME enable/disable
		actionItems.add(applyAvMenuItem); //FIXME enable/disable
		actionItems.add(pollCreateMenuItem);
		actionItems.add(pollResultMenuItem); //FIXME enable/disable
		actionItems.add(pollVoteMenuItem); //FIXME enable/disable
		actionItems.add(sipDialerMenuItem);
		actionItems.add(new RoomMenuItem(WebSession.getString(1126), WebSession.getString(1490)));
		actionsMenu.setChildren(actionItems);
		menu.add(actionsMenu);
		return menu;
	}
	
	@Override
	public void onMenuPanelLoad(AjaxRequestTarget target) {
		target.add(getMainPage().getHeader().setVisible(false), getMainPage().getMenu().setVisible(false)
				, getMainPage().getTopLinks().setVisible(false));
		target.appendJavaScript("roomLoad();");
	}
	
	@Override
	public void cleanup(AjaxRequestTarget target) {
		target.add(getMainPage().getHeader().setVisible(true), getMainPage().getMenu().setVisible(true)
				, getMainPage().getTopLinks().setVisible(true));
		target.appendJavaScript("$(window).off('resize.openmeetings'); $('.room.video').dialog('destroy');");
	}

	private ResourceReference newResourceReference() {
		return new JavaScriptResourceReference(RoomPanel.class, "room.js");
	}
	
	@Override
	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);
		response.render(new PriorityHeaderItem(JavaScriptHeaderItem.forReference(newResourceReference())));
		response.render(OnDomReadyHeaderItem.forScript(aab.getCallbackScript()));
	}

	private List<RoomClient> getUsers() {
		List<RoomClient> list = new ArrayList<RoomPanel.RoomClient>();
		for (Client c : getRoomUsers(roomId)) {
			list.add(new RoomClient(c));
		}
		return list;
	}
	
	static class RoomClient implements Serializable {
		private static final long serialVersionUID = 1L;
		private final Client c;
		private final User u;
		
		RoomClient(Client c) {
			this.c = c;
			this.u = getBean(UserDao.class).get(c.getUserId());
		}
	}
	
	static class FilesTreeProvider implements ITreeProvider<FileExplorerItem> {
		private static final long serialVersionUID = 1L;
		Long roomId = null;

		FilesTreeProvider(Long roomId) {
			this.roomId = roomId;
		}
		
		public void detach() {
			// TODO LDM should be used
		}

		public boolean hasChildren(FileExplorerItem node) {
			return node.getId() <= 0 || Type.Folder == node.getType();
		}

		public Iterator<? extends FileExplorerItem> getChildren(FileExplorerItem node) {
			FileExplorerItemDao dao = getBean(FileExplorerItemDao.class);
			List<FileExplorerItem> list = null;
			if (node.getId() == 0) {
				list = dao.getByOwner(node.getOwnerId());
			} else if (node.getId() < 0) {
				list = dao.getByRoom(roomId);
			} else {
				list = dao.getByParent(node.getId());
			}
			return list.iterator();
		}

		public IModel<FileExplorerItem> model(FileExplorerItem object) {
			// TODO LDM should be used
			return Model.of(object);
		}

		@Override
		public Iterator<? extends FileExplorerItem> getRoots() {
			FileExplorerItem f = new FileExplorerItem();
			f.setRoomId(roomId);
			f.setType(Type.Folder);
			if (roomId == null) {
				f.setId(0L);
				f.setOwnerId(getUserId());
				f.setFileName(WebSession.getString(706));
			} else {
				f.setId(-roomId);
				f.setFileName(WebSession.getString(707));
			}
			return Arrays.asList(f).iterator();
		}
	}		
}
