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
package org.apache.openmeetings.data.flvrecord.listener.async;

import static org.apache.openmeetings.util.OpenmeetingsVariables.webAppRootKey;
import static org.red5.io.IoConstants.TYPE_AUDIO;
import static org.red5.server.net.rtmp.event.VideoData.FrameType.KEYFRAME;

import java.util.Date;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.openmeetings.db.dao.record.FlvRecordingMetaDataDao;
import org.apache.openmeetings.db.dao.record.FlvRecordingMetaDeltaDao;
import org.apache.openmeetings.db.entity.record.FlvRecordingMetaData;
import org.apache.openmeetings.db.entity.record.FlvRecordingMetaDelta;
import org.red5.io.ITag;
import org.red5.io.flv.impl.Tag;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.scope.IScope;
import org.slf4j.Logger;

public class StreamAudioWriter extends BaseStreamWriter {
	private static final Logger log = Red5LoggerFactory.getLogger(StreamAudioWriter.class, webAppRootKey);

	private int duration = 0;

	private Integer lastTimeStamp = -1;
	private Date lastcurrentTime = null;

	private int lastStreamPacketTimeStamp = -1;

	private long byteCount = 0;

	// Autowire is not possible
	protected final FlvRecordingMetaDeltaDao metaDeltaDao;
	protected final FlvRecordingMetaDataDao metaDataDao;

	private boolean isInterview = false;

	public StreamAudioWriter(String streamName, IScope scope, Long metaDataId, boolean isScreenData,
			boolean isInterview, FlvRecordingMetaDataDao metaDataDao, FlvRecordingMetaDeltaDao metaDeltaDao) {
		super(streamName, scope, metaDataId, isScreenData);

		this.metaDeltaDao = metaDeltaDao;
		this.metaDataDao = metaDataDao;
		this.isInterview = isInterview;

		FlvRecordingMetaData metaData = metaDataDao.get(metaDataId);
		metaData.setStreamReaderThreadComplete(false);
		metaDataDao.update(metaData);
	}

	@Override
	public void packetReceived(CachedEvent streampacket) {
		try {
			// We only care about audio at this moment
			if (isInterview || TYPE_AUDIO == streampacket.getDataType()) {
				if (streampacket.getTimestamp() <= 0) {
					log.warn("Negative TimeStamp");
					return;
				}
				// we should not skip audio data in case it is Audio only interview
				if (isInterview && isScreenData && startTimeStamp == -1 && KEYFRAME != streampacket.getFrameType()) {
					//skip until keyframe
					log.trace("no KEYFRAME, skipping");
					return;
				}
				IoBuffer data = streampacket.getData().asReadOnlyBuffer();
				if (data.limit() == 0) {
					log.trace("data.limit() == 0 ");
					return;
				}

				byteCount += data.limit();

				lastcurrentTime = streampacket.getCurrentTime();
				int timeStamp = streampacket.getTimestamp();
				Date virtualTime = streampacket.getCurrentTime();

				if (startTimeStamp == -1) {
					// Calculate the delta between the initial start and the first audio-packet data

					initialDelta = virtualTime.getTime() - startedSessionTimeDate.getTime();

					FlvRecordingMetaDelta metaDelta = new FlvRecordingMetaDelta();

					metaDelta.setDeltaTime(initialDelta);
					metaDelta.setFlvRecordingMetaDataId(metaDataId);
					metaDelta.setTimeStamp(0);
					metaDelta.setDebugStatus("INIT AUDIO");
					metaDelta.setIsStartPadding(true);
					metaDelta.setIsEndPadding(false);
					metaDelta.setDataLengthPacket(data.limit());
					metaDelta.setReceivedAudioDataLength(byteCount);
					metaDelta.setStartTime(startedSessionTimeDate);
					metaDelta.setPacketTimeStamp(streampacket.getTimestamp());

					Long deltaTimeStamp = virtualTime.getTime() - startedSessionTimeDate.getTime();

					metaDelta.setDuration(0);

					Long missingTime = deltaTimeStamp - 0;

					metaDelta.setMissingTime(missingTime);

					metaDelta.setCurrentTime(virtualTime);
					metaDelta.setDeltaTimeStamp(deltaTimeStamp);
					metaDelta.setStartTimeStamp(startTimeStamp);

					metaDeltaDao.addFlvRecordingMetaDelta(metaDelta);

					// That will be not bigger then long value
					startTimeStamp = streampacket.getTimestamp();
				}

				lastStreamPacketTimeStamp = streampacket.getTimestamp();

				timeStamp -= startTimeStamp;

				// Offset at the beginning is calculated above
				long deltaTime = lastTimeStamp == -1 ? 0 : timeStamp - lastTimeStamp;

				Long preLastTimeStamp = Long.parseLong(lastTimeStamp.toString());

				lastTimeStamp = timeStamp;

				if (deltaTime > 75) {
					FlvRecordingMetaDelta metaDelta = new FlvRecordingMetaDelta();

					metaDelta.setDeltaTime(deltaTime);
					metaDelta.setFlvRecordingMetaDataId(metaDataId);
					metaDelta.setTimeStamp(timeStamp);
					metaDelta.setDebugStatus("RUN AUDIO");
					metaDelta.setIsStartPadding(false);
					metaDelta.setLastTimeStamp(preLastTimeStamp);
					metaDelta.setIsEndPadding(false);
					metaDelta.setDataLengthPacket(data.limit());
					metaDelta.setReceivedAudioDataLength(byteCount);
					metaDelta.setStartTime(startedSessionTimeDate);
					metaDelta.setPacketTimeStamp(streampacket.getTimestamp());

					Date current_date = new Date();
					Long deltaTimeStamp = current_date.getTime() - startedSessionTimeDate.getTime();

					duration = Math.max(duration, timeStamp + writer.getOffset());
					metaDelta.setDuration(duration);

					Long missingTime = deltaTimeStamp - timeStamp;

					metaDelta.setMissingTime(missingTime);

					metaDelta.setCurrentTime(current_date);
					metaDelta.setDeltaTimeStamp(deltaTimeStamp);
					metaDelta.setStartTimeStamp(startTimeStamp);

					metaDeltaDao.addFlvRecordingMetaDelta(metaDelta);
				}

				log.trace("timeStamp :: " + timeStamp);
				ITag tag = new Tag();
				tag.setDataType(streampacket.getDataType());

				// log.debug("data.limit() :: "+data.limit());
				tag.setBodySize(data.limit());
				tag.setTimestamp(timeStamp);
				tag.setBody(data);

				writer.writeTag(tag);

			}
		} catch (Exception e) {
			log.error("[packetReceived]", e);
		}
	}

	@Override
	public void closeStream() {
		try {
			writer.close();
		} catch (Exception err) {
			log.error("[closeStream, close writer]", err);
		}

		try {
			// We do not add any End Padding or count the gaps for the
			// Screen Data, cause there is no!

			Date virtualTime = lastcurrentTime;
			log.debug("virtualTime: " + virtualTime);
			log.debug("startedSessionTimeDate: " + startedSessionTimeDate);

			long deltaRecordingTime = virtualTime == null ? 0 : virtualTime.getTime()
					- startedSessionTimeDate.getTime();

			log.debug("lastTimeStamp :closeStream: " + lastTimeStamp);
			log.debug("lastStreamPacketTimeStamp :closeStream: " + lastStreamPacketTimeStamp);
			log.debug("deltaRecordingTime :closeStream: " + deltaRecordingTime);

			long deltaTimePaddingEnd = deltaRecordingTime - lastTimeStamp - initialDelta;

			log.debug("deltaTimePaddingEnd :: " + deltaTimePaddingEnd);

			FlvRecordingMetaDelta metaDelta = new FlvRecordingMetaDelta();

			metaDelta.setDeltaTime(deltaTimePaddingEnd);
			metaDelta.setFlvRecordingMetaDataId(metaDataId);
			metaDelta.setTimeStamp(lastTimeStamp);
			metaDelta.setDebugStatus("END AUDIO");
			metaDelta.setIsStartPadding(false);
			metaDelta.setIsEndPadding(true);
			metaDelta.setDataLengthPacket(null);
			metaDelta.setReceivedAudioDataLength(byteCount);
			metaDelta.setStartTime(startedSessionTimeDate);
			metaDelta.setCurrentTime(new Date());

			metaDeltaDao.addFlvRecordingMetaDelta(metaDelta);

			// Write the complete Bit to the meta data, the converter task will wait for this bit!
			FlvRecordingMetaData metaData = metaDataDao.get(metaDataId);
			metaData.setStreamReaderThreadComplete(true);
			metaDataDao.update(metaData);

		} catch (Exception err) {
			log.error("[closeStream]", err);
		}
	}
}
