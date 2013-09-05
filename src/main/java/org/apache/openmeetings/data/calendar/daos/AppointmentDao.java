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
package org.apache.openmeetings.data.calendar.daos;

import static org.apache.openmeetings.OpenmeetingsVariables.webAppRootKey;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

import org.apache.openmeetings.data.basic.dao.ConfigurationDao;
import org.apache.openmeetings.data.calendar.management.MeetingMemberLogic;
import org.apache.openmeetings.data.conference.InvitationManager;
import org.apache.openmeetings.data.conference.dao.InvitationDao;
import org.apache.openmeetings.data.conference.dao.RoomDao;
import org.apache.openmeetings.data.user.UserManager;
import org.apache.openmeetings.data.user.dao.UserDao;
import org.apache.openmeetings.persistence.beans.calendar.Appointment;
import org.apache.openmeetings.persistence.beans.calendar.AppointmentCategory;
import org.apache.openmeetings.persistence.beans.calendar.AppointmentReminderTyps;
import org.apache.openmeetings.persistence.beans.calendar.MeetingMember;
import org.apache.openmeetings.persistence.beans.room.Room;
import org.apache.openmeetings.persistence.beans.user.User;
import org.apache.openmeetings.persistence.beans.user.User.Type;
import org.apache.openmeetings.utils.TimezoneUtil;
import org.apache.openmeetings.utils.math.CalendarPatterns;
import org.apache.openmeetings.web.app.WebSession;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class AppointmentDao {
	private static final Logger log = Red5LoggerFactory.getLogger(AppointmentDao.class, webAppRootKey);
	@PersistenceContext
	private EntityManager em;
	@Autowired
	private UserManager userManager;
	@Autowired
	private AppointmentCategoryDao appointmentCategoryDaoImpl;
	@Autowired
	private AppointmentReminderTypDao appointmentReminderTypDao;
	@Autowired
	private MeetingMemberDao meetingMemberDao;
	@Autowired
	private UserDao usersDao;
	@Autowired
	private RoomDao roomDao;
	@Autowired
	private ConfigurationDao cfgDao;
	@Autowired
	private InvitationManager invitationManager;
	@Autowired
	private MeetingMemberLogic meetingMemberLogic;
	@Autowired
	private TimezoneUtil timezoneUtil;
	@Autowired
	private InvitationDao invitationDao;

	/*
	 * insert, update, delete, select
	 */

	/**
	 * @author o.becherer Retrievment of Appointment for room
	 */
	// -----------------------------------------------------------------------------------------------
	public Appointment getAppointmentByRoom(Long room_id) throws Exception {
		log.debug("AppointMentDaoImpl.getAppointmentByRoom");

		String hql = "select a from Appointment a "
				+ "WHERE a.deleted <> :deleted "
				+ "AND a.room.rooms_id = :room_id ";

		TypedQuery<Appointment> query = em.createQuery(hql, Appointment.class);
		query.setParameter("deleted", true);
		query.setParameter("room_id", room_id);

		List<Appointment> appoint = query.getResultList();

		if (appoint.size() > 0) {
			return appoint.get(0);
		}

		return null;
	}

	// -----------------------------------------------------------------------------------------------

	public Appointment get(Long id) {
		TypedQuery<Appointment> query = em.createNamedQuery("getAppointmentById", Appointment.class);
		query.setParameter("id", id);

		Appointment appoint = null;
		try {
			appoint = query.getSingleResult();
		} catch (NoResultException ex) {
		}
		return appoint;
	}

	public Appointment getAppointmentByIdBackup(Long appointmentId) {
		String hql = "select a from Appointment a WHERE a.id = :id ";

		TypedQuery<Appointment> query = em.createQuery(hql, Appointment.class);
		query.setParameter("appointmentId", appointmentId);

		Appointment appoint = null;
		try {
			appoint = query.getSingleResult();
		} catch (NoResultException ex) {
		}

		return appoint;
	}

	public List<Appointment> getAppointments() {
		return em.createQuery(
				"SELECT a FROM Appointment a LEFT JOIN FETCH a.meetingMembers WHERE a.deleted = false "
				, Appointment.class).getResultList();
	}

	/**
	 * 
	 * @param appointmentName
	 * @param userId
	 * @param appointmentLocation
	 * @param appointmentDescription
	 * @param appointmentstart
	 * @param appointmentend
	 * @param isDaily
	 * @param isWeekly
	 * @param isMonthly
	 * @param isYearly
	 * @param categoryId
	 * @param remind
	 * @param room
	 * @return
	 */
	// ----------------------------------------------------------------------------------------------------------------------------
	public Long addAppointment(String appointmentName, Long userId,
			String appointmentLocation, String appointmentDescription,
			Date appointmentstart, Date appointmentend, Boolean isDaily,
			Boolean isWeekly, Boolean isMonthly, Boolean isYearly,
			Long categoryId, Long remind, Room room, Long language_id,
			Boolean isPasswordProtected, String password,
			Boolean isConnectedEvent) {
		try {

			Appointment ap = new Appointment();

			ap.setTitle(appointmentName);
			ap.setLocation(appointmentLocation);

			log.debug("addAppointment appointmentstart :1: "
					+ CalendarPatterns
							.getDateWithTimeByMiliSecondsWithZone(appointmentstart));
			log.debug("addAppointment appointmentend :1: "
					+ CalendarPatterns
							.getDateWithTimeByMiliSecondsWithZone(appointmentend));

			ap.setStart(appointmentstart);
			ap.setEnd(appointmentend);
			ap.setDescription(appointmentDescription);
			ap.setRemind(appointmentReminderTypDao
					.getAppointmentReminderTypById(remind));
			ap.setInserted(new Date());
			ap.setReminderEmailSend(false);
			ap.setDeleted(false);
			ap.setIsDaily(isDaily);
			ap.setIsWeekly(isWeekly);
			ap.setIsMonthly(isMonthly);
			ap.setIsYearly(isYearly);
			ap.setLanguage_id(language_id);
			ap.setPasswordProtected(isPasswordProtected);
			ap.setPassword(password);
			ap.setOwner(usersDao.get(userId));
			ap.setCategory(appointmentCategoryDaoImpl
					.getAppointmentCategoryById(categoryId));
			ap.setRoom(room);
			ap.setConnectedEvent(isConnectedEvent);

			ap = em.merge(ap);

			return ap.getId();
		} catch (Exception ex2) {
			log.error("[addAppointment]: ", ex2);
		}
		return null;
	}

	public Long addAppointmentObj(Appointment ap) {
		try {

			ap.setInserted(new Date());

			ap = em.merge(ap);

			return ap.getId();
		} catch (Exception ex2) {
			log.error("[addAppointmentObj]: ", ex2);
		}
		return null;
	}

	public Appointment update(Appointment a, Long userId) {
		User u = usersDao.get(userId);
		a.setOwner(u);
		Room r = a.getRoom();
		if (r.getRooms_id() == null) {
			r.setName(a.getTitle());
			r.setNumberOfPartizipants(cfgDao.getConfValue("calendar.conference.rooms.default.size", Long.class, "50"));
		}
		roomDao.update(r, userId);
		if (a.getId() == null) {
			a.setInserted(new Date());
			em.persist(a);
		} else {
			a.setUpdated(new Date());
			a =	em.merge(a);
		}
		// update meeting members
		List<MeetingMember> mmList = a.getMeetingMembers();
		if (mmList != null){
			for (MeetingMember mm : mmList){
				String urlPostfix = (mm.getUser().getType() == Type.contact) ? "" : "#room/" + r.getRooms_id();
					
				meetingMemberLogic.addMeetingMemberInvitation(mm, a,
						WebSession.get().getBaseUrl() + urlPostfix, u);
			}
		}
		return a;
	}
	
	// ----------------------------------------------------------------------------------------------------------------------------

	public Long updateAppointment(Appointment appointment) {
		if (appointment.getId() > 0) {
			try {
				if (appointment.getId() == null) {
					em.persist(appointment);
				} else {
					if (!em.contains(appointment)) {
						em.merge(appointment);
					}
				}
				return appointment.getId();
			} catch (Exception ex2) {
				log.error("[updateAppointment] ", ex2);
			}
		} else {
			log.error("[updateAppointment] " + "Error: No AppointmentId given");
		}
		return null;
	}

	public List<Appointment> getAppointmentsByRoomId(Long roomId) {
		try {

			String hql = "select a from Appointment a "
					+ "WHERE a.room.rooms_id = :roomId ";

			TypedQuery<Appointment> query = em.createQuery(hql,
					Appointment.class);
			query.setParameter("roomId", roomId);
			List<Appointment> ll = query.getResultList();

			return ll;
		} catch (Exception e) {
			log.error("[getAppointmentsByRoomId]", e);
		}
		return null;
	}

	private void updateConnectedEventsTimeOnly(Appointment ap,
			Date appointmentstart, Date appointmentend) {
		try {

			if (ap.getRoom() == null) {
				return;
			}

			List<Appointment> appointments = this.getAppointmentsByRoomId(ap
					.getRoom().getRooms_id());

			for (Appointment appointment : appointments) {

				if (!ap.getId().equals(
						appointment.getId())) {

					ap.setStart(appointmentstart);
					ap.setEnd(appointmentend);
					ap.setUpdated(new Date());
					if (ap.getId() == null) {
						em.persist(ap);
					} else {
						if (!em.contains(ap)) {
							em.merge(ap);
						}
					}

				}

			}

		} catch (Exception err) {
			log.error("[updateConnectedEvents]", err);
		}
	}

	private void updateConnectedEvents(Appointment ap, String appointmentName,
			String appointmentDescription, Date appointmentstart,
			Date appointmentend, Boolean isDaily, Boolean isWeekly,
			Boolean isMonthly, Boolean isYearly,
			AppointmentCategory appointmentCategory,
			AppointmentReminderTyps appointmentReminderTyps, @SuppressWarnings("rawtypes") List mmClient,
			Long users_id, String baseUrl, Long language_id,
			Boolean isPasswordProtected, String password) {
		try {

			if (ap.getRoom() == null) {
				return;
			}

			List<Appointment> appointments = this.getAppointmentsByRoomId(ap
					.getRoom().getRooms_id());

			for (Appointment appointment : appointments) {

				if (!ap.getId().equals(
						appointment.getId())) {

					appointment.setTitle(appointmentName);
					appointment.setStart(appointmentstart);
					appointment.setEnd(appointmentend);
					appointment
							.setDescription(appointmentDescription);
					appointment.setUpdated(new Date());
					appointment.setRemind(appointmentReminderTyps);
					appointment.setIsDaily(isDaily);
					appointment.setIsWeekly(isWeekly);
					appointment.setIsMonthly(isMonthly);
					appointment.setIsYearly(isYearly);
					appointment.setLanguage_id(language_id);
					appointment.setPasswordProtected(isPasswordProtected);
					appointment.setPassword(password);
					// ap.setUserId(usersDao.getUser(userId));
					appointment.setCategory(appointmentCategory);

					if (appointment.getId() == null) {
						em.persist(appointment);
					} else {
						if (!em.contains(appointment)) {
							em.merge(appointment);
						}
					}

				}

			}

		} catch (Exception err) {
			log.error("[updateConnectedEvents]", err);
		}
	}

	/**
	 * 
	 * @param appointmentId
	 * @param appointmentName
	 * @param appointmentDescription
	 * @param appointmentstart
	 * @param appointmentend
	 * @param isDaily
	 * @param isWeekly
	 * @param isMonthly
	 * @param isYearly
	 * @param categoryId
	 * @param remind
	 * @param mmClient
	 * @param users_id
	 * @return
	 */
	// ----------------------------------------------------------------------------------------------------------
	public Long updateAppointment(Long appointmentId, String appointmentName,
			String appointmentDescription, Date appointmentstart,
			Date appointmentend, Boolean isDaily, Boolean isWeekly,
			Boolean isMonthly, Boolean isYearly, Long categoryId, Long remind,
			@SuppressWarnings("rawtypes") List mmClient, Long users_id, String baseUrl, Long language_id,
			Boolean isPasswordProtected, String password, String appointmentLocation) {

		log.debug("AppointmentDAOImpl.updateAppointment");
		try {

			Appointment ap = this.get(appointmentId);

			AppointmentReminderTyps appointmentReminderTyps = appointmentReminderTypDao
					.getAppointmentReminderTypById(remind);
			AppointmentCategory appointmentCategory = appointmentCategoryDaoImpl
					.getAppointmentCategoryById(categoryId);

			boolean sendMail = !ap.getTitle().equals(appointmentName) ||
					!ap.getDescription().equals(appointmentDescription) ||
					!ap.getLocation().equals(appointmentLocation) ||
					!ap.getStart().equals(appointmentstart) ||
					!ap.end().equals(appointmentend);
			
			// change connected events of other participants
			if (ap.isConnectedEvent()) {
				this.updateConnectedEvents(ap, appointmentName,
						appointmentDescription, appointmentstart,
						appointmentend, isDaily, isWeekly, isMonthly, isYearly,
						appointmentCategory, appointmentReminderTyps, mmClient,
						users_id, baseUrl, language_id, isPasswordProtected,
						password);
			}

			// Update Invitation hash to new time
			invitationDao.updateInvitationByAppointment(appointmentId,
					appointmentstart, appointmentend);

			ap.setTitle(appointmentName);
			ap.setLocation(appointmentLocation);
			ap.setStart(appointmentstart);
			ap.setEnd(appointmentend);
			ap.setDescription(appointmentDescription);
			ap.setUpdated(new Date());
			ap.setRemind(appointmentReminderTyps);
			ap.setIsDaily(isDaily);
			ap.setIsWeekly(isWeekly);
			ap.setIsMonthly(isMonthly);
			ap.setIsYearly(isYearly);
			ap.setLanguage_id(language_id);
			ap.setPasswordProtected(isPasswordProtected);
			ap.setPassword(password);
			// ap.setUserId(usersDao.getUser(userId));
			ap.setCategory(appointmentCategory);

			if (ap.getId() == null) {
				em.persist(ap);
			} else {
				if (!em.contains(ap)) {
					em.merge(ap);
				}
			}

			// Adding Invitor as Meetingmember
			User user = userManager.getUserById(users_id);

			String invitorName = user.getFirstname() + " " + user.getLastname()
					+ " [" + user.getAdresses().getEmail() + "]";

			List<MeetingMember> meetingsRemoteMembers = meetingMemberDao
					.getMeetingMemberByAppointmentId(ap.getId());

			// to remove
			for (MeetingMember memberRemote : meetingsRemoteMembers) {

				boolean found = false;

				if (mmClient != null) {
					for (int i = 0; i < mmClient.size(); i++) {
						
						@SuppressWarnings("rawtypes")
						Map clientMemeber = (Map) mmClient.get(i);
						Long meetingMemberId = Long
								.valueOf(
										clientMemeber.get("meetingMemberId")
												.toString()).longValue();
						
						log.debug("DELETE newly CHECK meetingMemberId: {} VS {} -- ", meetingMemberId, memberRemote.getId());

						if (memberRemote.getId().equals(
								meetingMemberId)) {
							log.debug("AppointMentDAOImpl.updateAppointment  - member "
									+ meetingMemberId + " is to be removed!");
							// Notifying Member for Update
							found = true;
							break;
						}

					}
				}

				if (!found) {
					
					log.debug("DELETE getMeetingMemberId: {} -- ", memberRemote.getId());

					// Not in List in client delete it
					meetingMemberLogic.deleteMeetingMember(
							memberRemote.getId(), users_id,
							language_id);
					// meetingMemberDao.deleteMeetingMember(memberRemote.getMeetingMemberId());
				} else {
					// Notify member of changes
					invitationManager.updateInvitation(ap, memberRemote,
							users_id, language_id, invitorName, sendMail);

				}
			}

			// add items
			if (mmClient != null) {

				for (int i = 0; i < mmClient.size(); i++) {

					@SuppressWarnings("rawtypes")
					Map clientMember = (Map) mmClient.get(i);

					Long meetingMemberId = Long.valueOf(
							clientMember.get("meetingMemberId").toString())
							.longValue();

					boolean found = false;

					for (MeetingMember memberRemote : meetingsRemoteMembers) {
						if (memberRemote.getId().equals(
								meetingMemberId)) {
							found = true;
						}
					}

					if (!found) {

						// We need two different timeZones, the internal Java
						// Object
						// TimeZone, and
						// the one for the UI display object to map to, cause
						// the UI
						// only has around 24 timezones
						// and Java around 600++
						Long sendToUserId = 0L;
						TimeZone timezoneMember = null;
						if (clientMember.get("userId") != null) {
							sendToUserId = Long.valueOf(
									clientMember.get("userId").toString())
									.longValue();
						}

						String phone = "";
						// Check if this is an internal user, if yes use the
						// timezone from his profile otherwise get the timezones
						// from the variable jNameTimeZone
						if (sendToUserId > 0) {
							User interalUser = userManager
									.getUserById(sendToUserId);
							timezoneMember = timezoneUtil
									.getTimezoneByUser(interalUser);
							phone = interalUser.getPhoneForSMS();
						} else {
							// Get the internal-name of the timezone set in the
							// client object and convert it to a real one
							Object jName = clientMember.get("jNameTimeZone");
							if (jName == null) {
								log.error("jNameTimeZone not set in user object variable");
								jName = "";
							}
							timezoneMember = timezoneUtil
									.getTimezoneByInternalJName(jName
											.toString());
						}

						// Not In Remote List available - intern OR external user
						meetingMemberLogic.addMeetingMember(
								clientMember.get("firstname") == null ?
										clientMember.get("firstname").toString() : "",
								clientMember.get("lastname") == null ? 
										clientMember.get("lastname").toString() : "",
								"0", // member - Status
								"0", // appointment - Status
								appointmentId,
								null, // UserId
								clientMember.get("email").toString(), // Email
																		// to
																		// send
																		// to
								phone,
								baseUrl, // URL to send to
								sendToUserId, // sending To: External users have
												// a 0 here
								new Boolean(false), // invitor
								language_id, 
								isPasswordProtected, 
								password,
								timezoneMember, 
								invitorName);

					}

				}
			}

			return appointmentId;
		} catch (Exception ex2) {
			log.error("[updateAppointment]: ", ex2);
		}
		return null;

	}

	public Long updateAppointmentByTime(Long appointmentId,
			Date appointmentstart, Date appointmentend, Long users_id,
			String baseUrl, Long language_id) {

		log.debug("AppointmentDAOImpl.updateAppointment");
		try {

			Appointment ap = get(appointmentId);

			if (!ap.getInserted().equals(appointmentstart) ||
					!ap.end().equals(appointmentend)) {

			// change connected events of other participants
			if (ap.isConnectedEvent()) {
				this.updateConnectedEventsTimeOnly(ap, appointmentstart,
						appointmentend);
			}

			// Update Invitation hash to new time
			invitationDao.updateInvitationByAppointment(appointmentId,
					appointmentstart, appointmentend);

			ap.setStart(appointmentstart);
			ap.setEnd(appointmentend);
			ap.setUpdated(new Date());

			if (ap.getId() == null) {
				em.persist(ap);
				} else if (!em.contains(ap)) {
					em.merge(ap);
				}

			List<MeetingMember> meetingsRemoteMembers = meetingMemberDao
					.getMeetingMemberByAppointmentId(ap.getId());

			// Adding Invitor Name
			User user = userManager.getUserById(users_id);
			String invitorName = user.getFirstname() + " " + user.getLastname()
					+ " [" + user.getAdresses().getEmail() + "]";

			// Send notification of updated Event
			for (MeetingMember memberRemote : meetingsRemoteMembers) {

				// Notify member of changes
				invitationManager.updateInvitation(ap, memberRemote,
							users_id, language_id, invitorName, true);

			}
			}
			return appointmentId;
		} catch (Exception ex2) {
			log.error("[updateAppointmentByTime]: ", ex2);
		}
		return null;

	}

	// ----------------------------------------------------------------------------------------------------------

	public Long deleteAppointement(Long appointmentId) {
		log.debug("deleteAppointMent");
		try {

			Appointment app = get(appointmentId);
			app.setUpdated(new Date());
			app.setDeleted(true);

			if (app.getId() == null) {
				em.persist(app);
			} else {
				if (!em.contains(app)) {
					em.merge(app);
				}
			}
			return appointmentId;
		} catch (Exception ex2) {
			log.error("[deleteAppointement]: " + ex2);
		}
		return null;
	}

	public List<Appointment> getAppointmentsByRange(Long userId, Date start, Date end) {
		Calendar calstart = Calendar.getInstance();
		calstart.setTime(start);
		calstart.set(Calendar.HOUR, 0);

		Calendar calend = Calendar.getInstance();
		calend.setTime(end);
		calend.set(Calendar.HOUR, 23);
		calend.set(Calendar.MINUTE, 59);
		
		log.debug("Start " + calstart.getTime() + " End " + calend.getTime());

		TypedQuery<Appointment> query = em.createNamedQuery("appointmentsInRange", Appointment.class);
		query.setParameter("starttime", calstart.getTime());
		query.setParameter("endtime", calend.getTime());
		query.setParameter("userId", userId);
		
		List<Appointment> listAppoints = new ArrayList<Appointment>(query.getResultList()); 
		TypedQuery<Appointment> q1 = em.createNamedQuery("joinedAppointmentsInRange", Appointment.class);
		q1.setParameter("starttime", calstart.getTime());
		q1.setParameter("endtime", calend.getTime());
		q1.setParameter("userId", userId);
		for (Appointment a : q1.getResultList()) {
			a.setConnectedEvent(true); //TODO need to be reviewed
			listAppoints.add(a);
		}

		return listAppoints;
	}

	public List<Appointment> getAppointmentsByCat(Long categoryId) {
		try {

			String hql = "select a from Appointments a "
					+ "WHERE a.deleted false "
					+ "AND a.appointmentCategory.categoryId = :categoryId";

			TypedQuery<Appointment> query = em.createQuery(hql,
					Appointment.class);
			query.setParameter("categoryId", categoryId);

			List<Appointment> listAppoints = query.getResultList();
			return listAppoints;
		} catch (Exception ex2) {
			log.error("[getAppointements]: ", ex2);
		}
		return null;
	}

	// next appointment to select date
	public Appointment getNextAppointment(Date appointmentStarttime) {
		try {

			String hql = "select a from Appointment a "
					+ "WHERE a.deleted false "
					+ "AND a.start > :appointmentStarttime ";

			TypedQuery<Appointment> query = em.createQuery(hql, Appointment.class);
			query.setParameter("appointmentStarttime", appointmentStarttime);

			Appointment appoint = null;
			try {
				appoint = query.getSingleResult();
			} catch (NoResultException ex) {
			}

			return appoint;
		} catch (Exception ex2) {
			log.error("[getNextAppointmentById]: ", ex2);
		}
		return null;
	}

	public List<Appointment> searchAppointmentsByName(String name) {
		try {

			String hql = "select a from Appointment a "
					+ "WHERE a.deleted false "
					+ "AND a.title LIKE :appointmentName";

			TypedQuery<Appointment> query = em.createQuery(hql,
					Appointment.class);
			query.setParameter("appointmentName", name);

			List<Appointment> listAppoints = query.getResultList();

			return listAppoints;
		} catch (Exception ex2) {
			log.error("[searchAppointmentsByName]: ", ex2);
		}
		return null;
	}

	/**
	 * @author becherer
	 * @param userId
	 * @return
	 */
	public List<Appointment> getTodaysAppointmentsbyRangeAndMember(Long userId) {
		log.debug("getAppoitmentbyRangeAndMember : UserID - " + userId);

		TimeZone timeZone = timezoneUtil.getTimezoneByUser(usersDao.get(userId));

		Calendar startCal = Calendar.getInstance(timeZone);
		startCal.set(Calendar.MINUTE, 0);
		startCal.set(Calendar.HOUR, 0);
		startCal.set(Calendar.SECOND, 1);

		Calendar endCal = Calendar.getInstance(timeZone);
		endCal.set(Calendar.MINUTE, 23);
		endCal.set(Calendar.HOUR, 59);
		endCal.set(Calendar.SECOND, 59);

		TypedQuery<Appointment> query = em.createNamedQuery("appointmentsInRangeByUser", Appointment.class);

		query.setParameter("userId", userId);

		query.setParameter("starttime", startCal.getTime());
		query.setParameter("endtime", endCal.getTime());

		List<Appointment> listAppoints = query.getResultList();
		return listAppoints;
	}

	/**
	 * Get the meetings according to a time range. It starts by now to
	 * Calendar.getInstance().getTime().getTime() + milliseconds
	 * 
	 * @author o.becherer,seba.wagner
	 * @param milliseconds
	 *            to get events in the past make milliseconds < 0
	 * @param isReminderEmailSend
	 *            if null all events in the time range, if false or true the
	 *            param is set
	 * @return
	 */
	public List<Appointment> getAppointmentsForAllUsersByTimeRangeStartingNow(
			long milliseconds, Boolean isReminderEmailSend) {
		try {

			String hql = "SELECT app from MeetingMember mm "
					+ "JOIN mm.appointment as app "
					+ "WHERE mm.deleted <> :mm_deleted "
					+ "AND app.deleted <> :app_deleted "
					+ "AND app.start between :starttime AND :endtime ";

			if (isReminderEmailSend != null) {
				hql += "AND (app.reminderEmailSend = :isReminderEmailSend) ";
			}

			Calendar startCal = Calendar.getInstance();
			if (milliseconds < 0) {
				startCal.setTimeInMillis(startCal.getTimeInMillis()+milliseconds);
			}
			Calendar endCal = Calendar.getInstance();
			if (milliseconds > 0) {
				endCal.setTimeInMillis(endCal.getTimeInMillis()+milliseconds);
			}

			TypedQuery<Appointment> query = em.createQuery(hql,
					Appointment.class);
			
			Timestamp startStamp = new Timestamp(startCal.getTime().getTime());
            Timestamp stopStamp = new Timestamp(endCal.getTime().getTime());
            
            log.debug("startStamp "+startStamp);
            log.debug("stopStamp "+stopStamp);

			query.setParameter("mm_deleted", true);
			query.setParameter("app_deleted", true);
			query.setParameter("starttime", startStamp);
			query.setParameter("endtime", stopStamp);
			if (isReminderEmailSend != null) {
				query.setParameter("isReminderEmailSend", isReminderEmailSend);
			}

			List<Appointment> listAppoints = query.getResultList();

			return listAppoints;
		} catch (Exception e) {
			log.error("Error in getAppointmentsForAllUsersByTimeRangeStartingNow : ", e);
			return null;
		}
	}

	// ---------------------------------------------------------------------------------------------

	public Appointment getAppointmentByRoomId(Long user_id, Long rooms_id) {
		try {

			String hql = "select a from Appointment a "
					+ "WHERE a.deleted <> :deleted "
					+ "AND a.owner.user_id = :user_id "
					+ "AND a.room.rooms_id = :rooms_id ";

			TypedQuery<Appointment> query = em.createQuery(hql,
					Appointment.class);

			query.setParameter("deleted", true);
			query.setParameter("user_id", user_id);
			query.setParameter("rooms_id", rooms_id);

			List<Appointment> listAppoints = query.getResultList();

			if (listAppoints.size() > 0) {
				return listAppoints.get(0);
			}

			return null;

		} catch (Exception e) {
			log.error("[getAppointmentByRoomId]", e);
			return null;
		}
	}

}
