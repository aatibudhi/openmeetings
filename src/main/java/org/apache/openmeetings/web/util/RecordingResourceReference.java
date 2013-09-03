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
package org.apache.openmeetings.web.util;

import static org.apache.openmeetings.OpenmeetingsVariables.webAppRootKey;
import static org.apache.openmeetings.web.app.Application.getBean;
import static org.apache.openmeetings.web.app.WebSession.getUserId;
import static org.red5.logging.Red5LoggerFactory.getLogger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.input.BoundedInputStream;
import org.apache.openmeetings.data.flvrecord.FlvRecordingDao;
import org.apache.openmeetings.data.user.dao.UserDao;
import org.apache.openmeetings.persistence.beans.domain.Organisation_Users;
import org.apache.openmeetings.persistence.beans.flvrecord.FlvRecording;
import org.apache.openmeetings.web.app.WebSession;
import org.apache.wicket.protocol.http.servlet.ResponseIOException;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.AbstractResource;
import org.apache.wicket.request.resource.ContentDisposition;
import org.apache.wicket.request.resource.IResource;
import org.apache.wicket.request.resource.IResource.Attributes;
import org.apache.wicket.request.resource.ResourceReference;
import org.apache.wicket.util.lang.Bytes;
import org.apache.wicket.util.resource.FileResourceStream;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.ResourceStreamNotFoundException;
import org.apache.wicket.util.string.StringValue;
import org.slf4j.Logger;

public abstract class RecordingResourceReference extends ResourceReference {
	private static final long serialVersionUID = 1L;
	private static final Logger log = getLogger(RecordingResourceReference.class, webAppRootKey);

	public RecordingResourceReference(Class<? extends RecordingResourceReference> clazz) {
		super(clazz, "recordings");
	}

	@Override
	public IResource getResource() {
		return new AbstractResource() {
			private static final long serialVersionUID = 1L;
			private final static String ACCEPT_RANGES_HEADER = "Accept-Ranges";
			private final static String RANGE_HEADER = "Range";
			private final static String CONTENT_RANGE_HEADER = "Content-Range";
			private final static String RANGES_BYTES = "bytes";
			private File file;
			private boolean isRange = false;
			private int start = 0;
			private int end = 0;
			
			private IResourceStream getResourceStream() {
				return file == null ? null : new FileResourceStream(file) {
					private static final long serialVersionUID = 2546785247219805747L;
					private transient BoundedInputStream bi;

					@Override
					public InputStream getInputStream() throws ResourceStreamNotFoundException {
						if (bi == null) {
							bi = new BoundedInputStream(super.getInputStream(), isRange ? end + start + 1 : -1);
							try {
								bi.skip(start);
							} catch (IOException e) {
								throw new ResourceStreamNotFoundException(e);
							}
						}
						return bi;
					}
					
					@Override
					public Bytes length() {
						return Bytes.bytes(isRange ? end - start + 1 : file.length());
					}
					
					@Override
					public void close() throws IOException {
						if (bi != null) {
							bi.close(); //also will close original stream
							bi = null;
						}
					}
					
					@Override
					public String getContentType() {
						return RecordingResourceReference.this.getContentType();
					}
				};
			}
			
			@Override
			protected ResourceResponse newResourceResponse(Attributes attributes) {
				ResourceResponse rr = new ResourceResponse();
				FlvRecording r = getRecording(attributes);
				if (r != null) {
					isRange = false;
					file = getFile(r);
					rr.setFileName(getFileName(r));
					rr.setContentType(RecordingResourceReference.this.getContentType());
					rr.setContentDisposition(ContentDisposition.INLINE);
					rr.getHeaders().addHeader(ACCEPT_RANGES_HEADER, RANGES_BYTES);
					String range = ((HttpServletRequest)attributes.getRequest().getContainerRequest()).getHeader(RANGE_HEADER);
					if (range != null && range.startsWith(RANGES_BYTES)) {
						String[] bounds = range.substring(RANGES_BYTES.length() + 1).split("-"); //TODO open ranges !!
						if (bounds != null && bounds.length > 1) {
							isRange = true;
							start = Integer.parseInt(bounds[0]);
							end = Integer.parseInt(bounds[1]);
							//Content-Range: bytes 229376-232468/232469
							rr.getHeaders().addHeader(CONTENT_RANGE_HEADER, String.format("%s %d-%d/%d", RANGES_BYTES, start, end, file.length()));
						}
					}
					final IResourceStream rStream = getResourceStream();
					rr.setContentLength(rStream.length().bytes());
					try {
						final InputStream  s = rStream.getInputStream();
						rr.setWriteCallback(new WriteCallback() {
							@Override
							public void writeData(Attributes attributes) throws IOException {
								try {
									writeStream(attributes, s);
								} catch (ResponseIOException e) {
									if (!isRange) {
										log.error("Error while playing the stream", e);
									}
									// in case of range operations we expecting such exceptions 
								} finally {
									rStream.close();
								}
							}
						});
					} catch (ResourceStreamNotFoundException e1) {
						rr.setError(HttpServletResponse.SC_NOT_FOUND);
					}
				} else {
					rr.setError(HttpServletResponse.SC_NOT_FOUND);
				}
				return rr;
			}
		};
	}
	
	abstract String getContentType();
	abstract String getFileName(FlvRecording r);
	abstract File getFile(FlvRecording r);
	
	private FlvRecording getRecording(Attributes attributes) {
		PageParameters params = attributes.getParameters();
		StringValue id = params.get("id");
		if (WebSession.get().isSignedIn() && !id.isEmpty()) {
			FlvRecording r = getBean(FlvRecordingDao.class).getFlvRecordingById(id.toLongObject());
			if (r.getOwnerId() == null || getUserId() == r.getOwnerId()) {
				return r;
			}
			//FIXME UGLY need to be optimized
			for (Organisation_Users ou : getBean(UserDao.class).get(getUserId()).getOrganisation_users()) {
				if (ou.getOrganisation().getOrganisation_id().equals(r.getOrganization_id())) {
					return r;
				}
			}
			//TODO investigate if these checks are enough
		}
		return null;
	}
}
