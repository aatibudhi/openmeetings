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

import static org.apache.openmeetings.util.OmFileHelper.getHumanSize;
import static org.apache.openmeetings.web.app.Application.getBean;
import static org.apache.openmeetings.web.app.WebSession.getUserId;

import org.apache.openmeetings.db.dao.record.FlvRecordingDao;
import org.apache.openmeetings.db.dao.user.UserDao;
import org.apache.openmeetings.db.dto.record.RecordingContainerData;
import org.apache.openmeetings.db.entity.file.FileItem;
import org.apache.openmeetings.db.entity.record.FlvRecording;
import org.apache.openmeetings.db.entity.user.Organisation;
import org.apache.openmeetings.db.entity.user.OrganisationUser;
import org.apache.openmeetings.web.common.UserPanel;
import org.apache.openmeetings.web.common.tree.FileItemTree;
import org.apache.openmeetings.web.common.tree.FileTreePanel;
import org.apache.openmeetings.web.common.tree.MyRecordingTreeProvider;
import org.apache.openmeetings.web.common.tree.PublicRecordingTreeProvider;
import org.apache.wicket.ajax.AjaxRequestTarget;

public class RecordingsPanel extends UserPanel {
	private static final long serialVersionUID = 1L;
	private final VideoPlayer video = new VideoPlayer("video");
	private final VideoInfo info = new VideoInfo("info");
	
	public RecordingsPanel(String id) {
		super(id);
		add(new FileTreePanel("tree") {
			private static final long serialVersionUID = 1L;

			@Override
			public void defineTrees() {
				selectedFile.setObject(new FlvRecording());
				treesView.add(selected = new FileItemTree<FlvRecording>(treesView.newChildId(), this, new MyRecordingTreeProvider()));
				treesView.add(new FileItemTree<FlvRecording>(treesView.newChildId(), this, new PublicRecordingTreeProvider(null, null)));
				for (OrganisationUser ou : getBean(UserDao.class).get(getUserId()).getOrganisationUsers()) {
					Organisation o = ou.getOrganisation();
					treesView.add(new FileItemTree<FlvRecording>(treesView.newChildId(), this, new PublicRecordingTreeProvider(o.getId(), o.getName())));
				}
			}
			
			@Override
			public void updateSizes() {
				RecordingContainerData sizeData = getBean(FlvRecordingDao.class).getRecordingContainerData(getUserId());
				if (sizeData != null) {
					homeSize.setObject(getHumanSize(sizeData.getUserHomeSize()));
					publicSize.setObject(getHumanSize(sizeData.getPublicFileSize()));
				}
			}
			
			@Override
			public void update(AjaxRequestTarget target, FileItem f) {
				video.update(target, (FlvRecording)f);
				info.update(target, (FlvRecording)f);
			}
			
			@Override
			public void createFolder(String name) {
				createRecordingFolder(name);
			}
		});
		add(video, info);
	}
}
