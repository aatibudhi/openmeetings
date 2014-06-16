/**
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

function getUserId(uid) { return 'user' + uid; }

function addUser(u, uld) {
	var s = u.firstname + ' ' + u.lastname;
	var d = $('<div class="user ui-corner-all ui-widget-content"></div>').attr('id', getUserId(u.uid))
		.attr('data-id', u.id).text(s);
	if (u.current) {
		d.addClass('current');
	}
	uld.append(d);
	//TODO add activity
}

function removeUser(id) {
	$('#' + id).remove();//TODO replace with 'ends-with-id'
	//TODO add activity
}

function roomMessage(m) {
	if (m && m.type == "room") {
		//TODO add timestamp support
		switch (m.msg) {
			case "users":
				var uld = $('.user.list');
				var ulist = [];
				uld.children('[id^="user"]').each(function() {
					ulist.push(this.id); 
				});
				for (var i = 0; i < m.users.length; ++i) {
					var u = m.users[i];
					var id = getUserId(u.uid);
					if ($('#' + id).length == 0) {
						addUser(u, uld);
					} else {
						var idx = ulist.indexOf(id);
						if (idx > -1) {
							ulist.splice(idx, 1);
						}
					}
				}
				for (var i = 0; i < ulist.length; ++i) {
					removeUser(ulist[i]);
				}
				break;
			case "addUser":
				var id = getUserId(m.user.uid);
				if ($('#' + id).length == 0) {
					addUser(m.user, $('.user.list'));
				}
				break;
			case "removeUser":
				removeUser(getUserId(m.uid));
				break;
		}
	}
}

function initVideo(sid, roomid, audioOnly, interview, labels) {
	var options = {sid: sid, roomid: roomid, audioOnly: audioOnly, interview: interview, bgcolor: "#ffffff"
		, width: 570, height: 900
		, resolutions: JSON.stringify([{label: "4:3 (~6 KByte/sec)", width: 40, height: 30}
			, {label: "4:3 (~12 KByte/sec)", width: 80, height: 60}
			, {label: "4:3 (~20 KByte/sec)", width: 120, height: 90, default: true}
			, {label: "QQVGA 4:3 (~36 KByte/sec)", width: 160, height: 120}
			, {label: "4:3 (~40 KByte/sec)", width: 240, height: 180}
			, {label: "HVGA 4:3 (~56 KByte/sec)", width: 320, height: 240}
			, {label: "4:3  (~60 KByte/sec)", width: 480, height: 360}
			, {label: "4:3 (~68 KByte/sec)", width: 640, height: 480}
			, {label: "XGA 4:3", width: 1024, height: 768}
			, {label: "16:9", width: 256, height: 150}
			, {label: "WQVGA 9:5", width: 432, height: 240}
			, {label: "pseudo 16:9", width: 480, height: 234}
			, {label: "16:9", width: 512, height: 300}
			, {label: "nHD 16:9", width: 640, height: 360}
			, {label: "16:9", width: 1024, height: 600}])
		, labels: JSON.stringify(labels)
		};
	var type = 'application/x-shockwave-flash';
	var src = 'public/main.swf?cache' + new Date().getTime();
	var r = $('<div class="video">');
	var o = $('<object>').attr('type', type).attr('data', src).attr('width', options.width).attr('height', options.height);
	o.append($('<param>').attr('name', 'quality').attr('value', 'best'));
	o.append($('<param>').attr('name', 'wmode').attr('value', 'transparent'));
	o.append($('<param>').attr('name', 'allowscriptaccess').attr('value', 'sameDomain'));
	o.append($('<param>').attr('name', 'allowfullscreen').attr('value', 'false'));
	o.append($('<param>').attr('name', 'flashvars').attr('value', $.param(options)));
	$('#roomMenu').parent().append(r.append(o));
	/*
			.attr('wmode', 'window').attr('allowfullscreen', true)
			.attr('width', options.width).attr('height', options.height)
			.attr('id', 'lzapp').attr('name', 'lzapp')
			.attr('flashvars', escape($.param(options)))
			.attr('swliveconnect', true).attr('align', 'middle')
			.attr('allowscriptaccess', 'sameDomain').attr('type', 'application/x-shockwave-flash')
			.attr('pluginspage', 'http://www.macromedia.com/go/getflashplayer')
	*/
	r.dialog({ width: options.width, height: options.height, dialogClass: "video" });
}

function setHeight() {
	var h = $(document).height() - $('#roomMenu').height();
	$(".room.sidebar.left").height(h);
	$(".room.wb.area").height(h);
}

$(document).ready(function() {
	$(window).on('resize.openmeetings', function() {
		setHeight();
	});
});
