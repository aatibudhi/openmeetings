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
package org.apache.openmeetings.web.user.record;

import static org.apache.openmeetings.utils.OmFileHelper.getMp4Recording;
import static org.apache.openmeetings.utils.OmFileHelper.getRecording;
import static org.apache.openmeetings.web.app.Application.getBean;
import static org.apache.openmeetings.web.app.WebSession.getUserId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.openmeetings.data.flvrecord.FlvRecordingDao;
import org.apache.openmeetings.data.user.dao.UsersDao;
import org.apache.openmeetings.persistence.beans.domain.Organisation_Users;
import org.apache.openmeetings.persistence.beans.flvrecord.FlvRecording;
import org.apache.openmeetings.web.app.WebSession;
import org.apache.openmeetings.web.common.AddFolderDialog;
import org.apache.openmeetings.web.common.ConfirmableAjaxLink;
import org.apache.openmeetings.web.common.UserPanel;
import org.apache.openmeetings.web.util.AjaxDownload;
import org.apache.openmeetings.web.util.Mp4RecordingResourceReference;
import org.apache.openmeetings.web.util.OggRecordingResourceReference;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxEventBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.AjaxEditableLabel;
import org.apache.wicket.extensions.markup.html.repeater.tree.DefaultNestedTree;
import org.apache.wicket.extensions.markup.html.repeater.tree.ITreeProvider;
import org.apache.wicket.extensions.markup.html.repeater.tree.content.Folder;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.repeater.ReuseIfModelsEqualStrategy;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.util.ListModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.util.resource.FileResourceStream;
import org.wicketstuff.html5.media.MediaSource;
import org.wicketstuff.html5.media.video.Html5Video;

public class RecordingsPanel extends UserPanel {
	private static final long serialVersionUID = 1321258690447136958L;
	private WebMarkupContainer info = new WebMarkupContainer("info");
	private WebMarkupContainer trees = new WebMarkupContainer("trees");
	private WebMarkupContainer video = new WebMarkupContainer("video");
	private WebMarkupContainer wait = new WebMarkupContainer("wait");
	private final Mp4RecordingResourceReference mp4res = new Mp4RecordingResourceReference();
	private final OggRecordingResourceReference oggres = new OggRecordingResourceReference();
	private final WebMarkupContainer player;
	private final IModel<List<MediaSource>> playerModel = new ListModel<MediaSource>(new ArrayList<MediaSource>());
	private RecordingTree myTree;
	private IModel<FlvRecording> rm = new CompoundPropertyModel<FlvRecording>(new FlvRecording());
	private RecordingTree selected;
	private Button dAVI = new Button("dAVI");
	private Button dFLV = new Button("dFLV");
	private AjaxDownload download = new AjaxDownload();

	public RecordingsPanel(String id) {
		super(id);
		rm.getObject().setFlvRecordingId(-3);
		rm.getObject().setOwnerId(getUserId());
		final AddFolderDialog addFolder = new AddFolderDialog("addFolder", WebSession.getString(712)) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onSubmit(AjaxRequestTarget target) {
				FlvRecording f = new FlvRecording();
				f.setFileName(getModelObject());
				f.setInsertedBy(getUserId());
				f.setInserted(new Date());
				f.setIsFolder(true);
				f.setIsImage(false);
				f.setIsPresentation(false);
				f.setIsRecording(true);
				long parentId = rm.getObject().getFlvRecordingId();
				f.setParentFileExplorerItemId(parentId > 0 ? parentId : 0);
				f.setOwnerId(rm.getObject().getOwnerId());
				getBean(FlvRecordingDao.class).updateFlvRecording(f);
				target.add(trees);
			}
		};
		add(addFolder);
		add(new WebMarkupContainer("create").add(new AjaxEventBehavior("onclick") {
			private static final long serialVersionUID = -110084769805785972L;

			@Override
			protected void onEvent(AjaxRequestTarget target) {
				addFolder.open(target);
			}
		}));
		add(new WebMarkupContainer("refresh").add(new AjaxEventBehavior("onclick") {
			private static final long serialVersionUID = -110084769805785972L;

			@Override
			protected void onEvent(AjaxRequestTarget target) {
				target.add(trees);
				for (FlvRecording r : myTree.getModelObject()) {
					myTree.expand(r);
				}
			}
		}));
		add(new ConfirmableAjaxLink("trash", 713) {
			private static final long serialVersionUID = 4145757350556878550L;

			@Override
			public void onClick(AjaxRequestTarget target) {
				long id = rm.getObject().getFlvRecordingId();
				if (id > 0) {
					getBean(FlvRecordingDao.class).deleteFlvRecording(id);
				}
				target.add(trees);
			}
		});
		add(trees
			.add(myTree = new RecordingTree("myrecordings", new MyRecordingTreeProvider()))
			.add(new RecordingTree("publicrecordings", new PublicRecordingTreeProvider()))
			.setOutputMarkupId(true)
			);
		add(new Label("homeSize", ""));
		add(new Label("publicSize", ""));
		add(info.setDefaultModel(rm).add(
				new Label("fileName"), new Label("fileSize")
				, new Label("recordEnd"), new Label("room_id")
				, dFLV.setEnabled(false).setOutputMarkupId(true), dAVI.setEnabled(false).setOutputMarkupId(true)
				).setOutputMarkupId(true)
			);
		player = new Html5Video("player", playerModel) {
			private static final long serialVersionUID = -6058309447222765121L;

			@Override
			protected boolean isAutoPlay() {
				return false;
			}
			
			@Override
			protected boolean isControls() {
				return true;
			}
		};
		dAVI.add(new AjaxEventBehavior("click"){
			private static final long serialVersionUID = 5487675326677710761L;

			@Override
			protected void onEvent(AjaxRequestTarget target) {
				download.setFileName(rm.getObject().getAlternateDownload());
				download.setResourceStream(new FileResourceStream(getRecording(rm.getObject().getAlternateDownload())));
				download.initiate(target);
			}
		});
		dFLV.add(new AjaxEventBehavior("click"){
			private static final long serialVersionUID = 8630730552169483717L;

			@Override
			protected void onEvent(AjaxRequestTarget target) {
				download.setFileName(rm.getObject().getFileHash());
				download.setResourceStream(new FileResourceStream(getRecording(rm.getObject().getFileHash())));
				download.initiate(target);
			}
		});
		add(video.add(wait.setVisible(false), player.setVisible(false)).setOutputMarkupId(true));
		add(download);
	}

	//FIXME need to be generalized to use as Room files explorer
	class RecordingTree extends DefaultNestedTree<FlvRecording> {
		private static final long serialVersionUID = 2527395034256868022L;

		public RecordingTree(String id, ITreeProvider<FlvRecording> tp) {
			super(id, tp);
			setItemReuseStrategy(new ReuseIfModelsEqualStrategy());
		}
		
		@Override
		protected Component newContentComponent(String id, IModel<FlvRecording> node) {
			return new Folder<FlvRecording>(id, this, node) {
				private static final long serialVersionUID = 1020976220467419084L;

				@Override
				protected Component newLabelComponent(String id, final IModel<FlvRecording> lm) {
					FlvRecording r = lm.getObject();
					if (r.getIsFolder() != null && r.getIsFolder()) {
						return new AjaxEditableLabel<String>(id, newLabelModel(lm)) {
							private static final long serialVersionUID = -6631089550858911148L;
							
							@Override
							protected void onSubmit(AjaxRequestTarget target) {
								super.onSubmit(target);
								FlvRecording r = getModelObject();
								r.setFileName(getEditor().getModelObject());
								getBean(FlvRecordingDao.class).updateFlvRecording(r);
							}
							
							@Override
							public void onEdit(AjaxRequestTarget target) {
								if (isSelected()) {
									super.onEdit(target);
								}
							}
						};
					} else {
						return super.newLabelComponent(id, lm);
					}
				}
				
				@Override
				protected boolean isSelected() {
					return getModelObject().getFlvRecordingId() == rm.getObject().getFlvRecordingId();
				}
				
				@Override
				protected boolean isClickable() {
					return true;
				}
				
				@Override
				protected void onClick(AjaxRequestTarget target) {
					FlvRecording r = getModelObject();
					FlvRecording _prev = rm.getObject();
					if (r.getFlvRecordingId() > 0) {
						rm.setObject(r);
					}
					if (selected != null && _prev != null) {
						selected.updateBranch(_prev, target);
						selected.updateNode(_prev, target);
					}
 					selected = RecordingTree.this;
					if (r.getIsFolder() == null || r.getIsFolder()) {
						if (getState(r) == State.COLLAPSED) {
							super.onClick(target);
						}
						updateBranch(r, target);
					} else {
						boolean videoExists = getMp4Recording(r.getFileHash()).exists();
						if (videoExists) {
							PageParameters pp = new PageParameters().add("id", r.getFlvRecordingId());
							playerModel.setObject(Arrays.asList(
									new MediaSource("" + getRequestCycle().urlFor(mp4res, pp), "video/mp4")
									, new MediaSource("" + getRequestCycle().urlFor(oggres, pp), "video/ogg")));
						}
						player.setVisible(videoExists);
						target.add(video, info, dAVI.setEnabled(getRecording(r.getAlternateDownload()).exists()));
						target.add(video, info, dFLV.setEnabled(getRecording(r.getFileHash()).exists()));
						updateNode(r, target);
					}
				}
				
				@Override
				protected String getOtherStyleClass(FlvRecording t) {
					String style;
					if (t.getFlvRecordingId() == -2) {
						style = "my-recordings om-icon";
					} else if (t.getFlvRecordingId() == -1) {
						style = "public-recordings om-icon";
					} else {
						style = t.getIsFolder() ? super.getOtherStyleClass(t)
								: (getMp4Recording(t.getFileHash()).exists() ? "recording om-icon" : "broken-recording om-icon");
					}
					if (isSelected()) {
						style += " selected";
					}
					return style;
				}
				
				@Override
				protected String getOpenStyleClass() {
					String style;
					FlvRecording r = getModelObject();
					if (r.getFlvRecordingId() == -2) {
						style = "my-recordings om-icon";
					} else if (r.getFlvRecordingId() == -1) {
						style = "public-recordings om-icon";
					} else {
						style = super.getOpenStyleClass();
					}
					return style;
				}
				
				@Override
				protected String getClosedStyleClass() {
					String style;
					FlvRecording r = getModelObject();
					if (r.getFlvRecordingId() == -2) {
						style = "my-recordings om-icon";
					} else if (r.getFlvRecordingId() == -1) {
						style = "public-recordings om-icon";
					} else {
						style = super.getClosedStyleClass();
					}
					return style;
				}
				
				@Override
				protected IModel<String> newLabelModel(IModel<FlvRecording> model) {
					return Model.of(model.getObject().getFileName());
				}
			};
		}
	}
	
	class MyRecordingTreeProvider extends RecordingTreeProvider {
		private static final long serialVersionUID = -4463900798616753927L;

		public Iterator<? extends FlvRecording> getRoots() {
			FlvRecording r = new FlvRecording();
			r.setFlvRecordingId(-2);
			r.setFileName(WebSession.getString(860));
			r.setOwnerId(getUserId());
			return Arrays.asList(r).iterator();
		}
		
		public Iterator<? extends FlvRecording> getChildren(FlvRecording node) {
			if (node.getFlvRecordingId() < 0) {
				return getBean(FlvRecordingDao.class).getFlvRecordingRootByOwner(getUserId()).iterator();
			} else {
				return super.getChildren(node);
			}
		}
	}
	
	class PublicRecordingTreeProvider extends RecordingTreeProvider {
		private static final long serialVersionUID = 5502610991599632079L;

		public Iterator<? extends FlvRecording> getRoots() {
			FlvRecording r = new FlvRecording();
			r.setFlvRecordingId(-1);
			r.setFileName(WebSession.getString(861));
			return Arrays.asList(r).iterator();
		}
		
		public Iterator<? extends FlvRecording> getChildren(FlvRecording node) {
			if (node.getFlvRecordingId() < 0) {
				List<FlvRecording> roots = new ArrayList<FlvRecording>();
				for (Organisation_Users ou : getBean(UsersDao.class).get(getUserId()).getOrganisation_users()) {
					roots.addAll(getBean(FlvRecordingDao.class).getFlvRecordingRootByPublic(ou.getOrganisation().getOrganisation_id()));
				}
				return roots.iterator();
			} else {
				return super.getChildren(node);
			}
		}
	}
	
	abstract class RecordingTreeProvider implements ITreeProvider<FlvRecording> {
		private static final long serialVersionUID = -3149843028275612342L;

		public void detach() {
			// TODO LDM should be used
		}

		public boolean hasChildren(FlvRecording node) {
			return node.getFlvRecordingId() < 0 || node.getIsFolder();
		}

		public Iterator<? extends FlvRecording> getChildren(FlvRecording node) {
			return getBean(FlvRecordingDao.class).getFlvRecordingByParent(node.getFlvRecordingId()).iterator();
		}

		public IModel<FlvRecording> model(FlvRecording object) {
			// TODO LDM should be used
			return Model.of(object);
		}
		
	}
}
