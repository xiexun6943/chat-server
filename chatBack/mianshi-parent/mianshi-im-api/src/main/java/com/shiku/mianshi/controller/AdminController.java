package com.shiku.mianshi.controller;

import cn.xyz.commons.constants.KConstants;
import cn.xyz.commons.constants.KConstants.Result;
import cn.xyz.commons.ex.BizException;
import cn.xyz.commons.ex.ServiceException;
import cn.xyz.commons.support.Callback;
import cn.xyz.commons.support.mongo.MongoOperator;
import cn.xyz.commons.utils.*;
import cn.xyz.commons.vo.JSONMessage;
import cn.xyz.mianshi.model.*;
import cn.xyz.mianshi.opensdk.entity.SkOpenAccount;
import cn.xyz.mianshi.opensdk.entity.SkOpenApp;
import cn.xyz.mianshi.opensdk.entity.SkOpenCheckLog;
import cn.xyz.mianshi.service.impl.LiveRoomManagerImpl;
import cn.xyz.mianshi.service.impl.RoomManagerImplForIM;
import cn.xyz.mianshi.service.impl.UserManagerImpl;
import cn.xyz.mianshi.utils.ConstantUtil;
import cn.xyz.mianshi.utils.KSessionUtil;
import cn.xyz.mianshi.utils.SKBeanUtils;
import cn.xyz.mianshi.vo.*;
import cn.xyz.mianshi.vo.LiveRoom.LiveRoomMember;
import cn.xyz.mianshi.vo.Room.Member;
import cn.xyz.mianshi.vo.User.UserLoginLog;
import cn.xyz.service.KXMPPServiceImpl;
import cn.xyz.service.KXMPPServiceImpl.MessageBean;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mongodb.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * ??????????????????
 *
 * @author luorc
 *
 */
@RestController
@RequestMapping("/console")
public class AdminController extends AbstractController {

	private Logger logger= LoggerFactory.getLogger(AdminController.class);

	public static final String LOGIN_USER_KEY = "LOGIN_USER";

	@Resource(name = "dsForRW")
	private Datastore dsForRW;

	@Resource(name = "dsForTigase")
	private Datastore dsForTigase;

	@Resource(name = "dsForRoom")
	private Datastore dsForRoom;


	private static RoomManagerImplForIM getRoomManagerImplForIM() {
		RoomManagerImplForIM roomManagerImplForIM = SKBeanUtils.getRoomManagerImplForIM();
		return roomManagerImplForIM;
	};

	private static LiveRoomManagerImpl getLiveRoomManager() {
		LiveRoomManagerImpl liveRoomManagerImpl = SKBeanUtils.getLiveRoomManager();
		return liveRoomManagerImpl;
	};

	private static UserManagerImpl getUserManager() {
		UserManagerImpl userManagerImpl = SKBeanUtils.getUserManager();
		return userManagerImpl;
	};

	@RequestMapping(value = "/config")
	public JSONMessage getConfig() {
		Config config = SKBeanUtils.getAdminManager().getConfig();
		config.setDistance(ConstantUtil.getAppDefDistance());
		return JSONMessage.success(null, config);
	}

	// ?????????????????????
	@RequestMapping(value = "/config/set", method = RequestMethod.POST)
	public JSONMessage setConfig(@ModelAttribute Config config) throws Exception {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getAdminManager().setConfig(config);
			return JSONMessage.success();
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	// ?????????????????????
	@RequestMapping(value = "/clientConfig/set")
	public JSONMessage setClientConfig(@ModelAttribute ClientConfig clientConfig) throws Exception{
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getAdminManager().setClientConfig(clientConfig);
			return JSONMessage.success();
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	@RequestMapping(value = "/clientConfig")
	public JSONMessage getClientConfig() {
		ClientConfig clientConfig = SKBeanUtils.getAdminManager().getClientConfig();
		return JSONMessage.success(null, clientConfig);
	}

	@RequestMapping(value = "/chat_logs", method = { RequestMethod.GET })
	public ModelAndView chat_logs(@RequestParam(defaultValue = "0") long startTime,
								  @RequestParam(defaultValue = "0") long endTime, @RequestParam(defaultValue = "0") int pageIndex,
								  @RequestParam(defaultValue = "10") int pageSize, HttpServletRequest request) {
		// User user = getUser();

		DBCollection dbCollection = dsForTigase.getDB().getCollection("shiku_msgs");
		BasicDBObject q = new BasicDBObject();
		// q.put("sender", user.getUserId());
		if (0 != startTime)
			q.put("ts", new BasicDBObject("$gte", startTime));
		if (0 != endTime)
			q.put("ts", new BasicDBObject("$lte", endTime));

		long total = dbCollection.count(q);
		List<DBObject> pageData = Lists.newArrayList();

		DBCursor cursor = dbCollection.find(q).skip(pageIndex * pageSize).limit(pageSize);
		while (cursor.hasNext()) {
			BasicDBObject dbObj = (BasicDBObject) cursor.next();
			if (1 == dbObj.getInt("direction")) {
				int sender = dbObj.getInt("receiver");
				dbObj.put("receiver_nickname", getUserManager().getUser(sender).getNickname());
			}
			pageData.add(dbObj);
		}
		request.setAttribute("page", new PageVO(pageData, total, pageIndex, pageSize));
		return new ModelAndView("chat_logs");
	}

	@RequestMapping(value = "/chat_logs_all")
	public JSONMessage chat_logs_all(@RequestParam(defaultValue = "0") long startTime,
									 @RequestParam(defaultValue = "0") long endTime, @RequestParam(defaultValue = "0") int sender,
									 @RequestParam(defaultValue = "0") int receiver, @RequestParam(defaultValue = "0") int page,
									 @RequestParam(defaultValue = "10") int limit,@RequestParam(defaultValue = "") String keyWord, HttpServletRequest request) throws Exception {
		DBCollection dbCollection = dsForTigase.getDB().getCollection("shiku_msgs");
		BasicDBObject q = new BasicDBObject();
		if (0 == receiver) {
			q.put("receiver", new BasicDBObject("$ne", 10005));
			q.put("direction", 0);
		} else {
			q.put("direction", 0);
			q.put("receiver", BasicDBObjectBuilder.start("$eq", receiver).add("$ne", 10005).get());
		}
		if (0 == sender) {
			q.put("sender", new BasicDBObject("$ne", 10005));
			q.put("direction", 0);
		} else {
			q.put("direction", 0);
			q.put("sender", BasicDBObjectBuilder.start("$eq", sender).add("$ne", 10005).get());
		}
		if(!StringUtil.isEmpty(keyWord)){
			q.put("content", new BasicDBObject(MongoOperator.REGEX,keyWord));
		}

		if (0 != startTime)
			q.put("ts", new BasicDBObject("$gte", startTime));
		if (0 != endTime)
			q.put("ts", new BasicDBObject("$lte", endTime));

		long total = dbCollection.count(q);
		List<DBObject> pageData = Lists.newArrayList();

		DBCursor cursor = dbCollection.find(q).sort(new BasicDBObject("_id", -1)).skip((page - 1) * limit).limit(limit);
		PageResult<DBObject> result = new PageResult<DBObject>();
		while (cursor.hasNext()) {
			BasicDBObject dbObj = (BasicDBObject) cursor.next();
			@SuppressWarnings("deprecation")
			String unescapeHtml3 = StringEscapeUtils.unescapeHtml3((String) dbObj.get("body"));
			JSONObject body = JSONObject.parseObject(unescapeHtml3);
			if (null != body.get("isEncrypt") && "1".equals(body.get("isEncrypt").toString())) {
				dbObj.put("isEncrypt", 1);
			} else {
				dbObj.put("isEncrypt", 0);
			}
			try {
				dbObj.put("sender_nickname", getUserManager().getNickName(dbObj.getInt("sender")));
			} catch (Exception e) {
				dbObj.put("sender_nickname", "??????");
			}
			try {
				dbObj.put("receiver_nickname", getUserManager().getNickName(dbObj.getInt("receiver")));
			} catch (Exception e) {
				dbObj.put("receiver_nickname", "??????");
			}
			try {
				dbObj.put("content",
						JSON.parseObject(dbObj.getString("body").replace("&quot;", "\""), Map.class).get("content"));
			} catch (Exception e) {
				dbObj.put("content", "--");
			}

			pageData.add(dbObj);

		}
		result.setData(pageData);
		result.setCount(total);
		return JSONMessage.success(result);
	}

	@RequestMapping(value = "/chat_logs_all/del", method = { RequestMethod.POST })
	public JSONMessage chat_logs_all_del(@RequestParam(defaultValue = "0") long startTime,
										 @RequestParam(defaultValue = "0") long endTime, @RequestParam(defaultValue = "0") int sender,
										 @RequestParam(defaultValue = "0") int receiver, @RequestParam(defaultValue = "0") int pageIndex,
										 @RequestParam(defaultValue = "25") int pageSize, HttpServletRequest request, HttpServletResponse response)
			throws Exception {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return JSONMessage.failure("????????????");
		}
		DBCollection dbCollection = dsForTigase.getDB().getCollection("shiku_msgs");
		BasicDBObject q = new BasicDBObject();

		if (0 == sender) {
			q.put("sender", new BasicDBObject("$ne", 10005));
		} else {
			q.put("sender", BasicDBObjectBuilder.start("$eq", sender).add("$ne", 10005).get());
		}
		if (0 == receiver) {
			q.put("receiver", new BasicDBObject("$ne", 10005));
		} else {
			q.put("receiver", BasicDBObjectBuilder.start("$eq", receiver).add("$ne", 10005).get());
		}
		if (0 != startTime)
			q.put("ts", new BasicDBObject("$gte", startTime));
		if (0 != endTime)
			q.put("ts", new BasicDBObject("$lte", endTime));
		dbCollection.remove(q);
		return JSONMessage.success();

	}

	@RequestMapping(value = "/deleteChatMsgs")
	public JSONMessage deleteChatMsgs(@RequestParam(defaultValue = "") String msgId,
									  @RequestParam(defaultValue = "0") int type) {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return JSONMessage.failure("????????????");
		}

		DBCollection dbCollection = dsForTigase.getDB().getCollection("shiku_msgs");
		BasicDBObject q = new BasicDBObject();
		try {
			if (0 == type) {
				if (StringUtil.isEmpty(msgId))
					return JSONMessage.failure("????????????");
				else {
					String[] msgIds = StringUtil.getStringList(msgId);
					for (String strMsgId : msgIds) {
						q.put("_id", new ObjectId(strMsgId));
						dbCollection.remove(q);
					}
				}
			} else if (1 == type) {
				// ?????????????????????????????????
				long onedayNextDay = DateUtil.getOnedayNextDay(DateUtil.currentTimeSeconds(), 30, 1);
				System.out.println("?????????????????????" + onedayNextDay);
				q.put("timeSend", new BasicDBObject("$lte", onedayNextDay));
				dbCollection.remove(q);
			} else if (2 == type) {
				final int num = 100000;
				int count = dbCollection.find().count();
				if (count <= num)
					throw new ServiceException("??????????????????" + num);
				// ?????????????????????????????????
				DBCursor cursor = dbCollection.find().sort(new BasicDBObject("timeSend", -1)).skip(num).limit(count);
				List<DBObject> list = cursor.toArray();
				for (DBObject dbObject : list) {
					dbCollection.remove(dbObject);
				}
				logger.info("??????" + num + "???????????????" + list.size());
			}
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getErrMessage());
		}
		return JSONMessage.success();
	}

	@RequestMapping(value = "/deleteRoom")
	public JSONMessage deleteRoom(@RequestParam(defaultValue = "") String roomId, @RequestParam(defaultValue = "0") Integer userId) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}

			DBCollection dbCollection = dsForTigase.getCollection(Room.class);
			getRoomManagerImplForIM().delete(new ObjectId(roomId),userId);
			dbCollection.remove(new BasicDBObject("_id", new ObjectId(roomId)));
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}
	}

	// ???????????????????????????
	@RequestMapping(value = "/inviteJoinRoom")
	public JSONMessage inviteJoinRoom(@RequestParam(defaultValue ="") String roomId,@RequestParam(defaultValue = "") String userIds,@RequestParam(defaultValue = "") Integer inviteUserId) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			if (StringUtil.isEmpty(userIds)) {
				return JSONMessage.failure("?????????????????????");
			}
			Room room = SKBeanUtils.getRoomManager().getRoom(new ObjectId(roomId));
			if (null == room)
				return JSONMessage.failure("??????????????? ????????????!");
			else if (-1 == room.getS())
				return JSONMessage.failure("???????????????????????????!");
			else {
				List<Integer> userIdList = StringUtil.getIntList(userIds, ",");
				if (room.getMaxUserSize() < room.getUserSize() + userIdList.size())
					return JSONMessage.failure("??????????????? ????????????!");
				User user = new User();
				user.setUserId(inviteUserId);
				user.setNickname("???????????????");
				getRoomManagerImplForIM().consoleJoinRoom(user, new ObjectId(roomId), userIdList);
				return JSONMessage.success();
			}
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ??????????????????
	 *
	 * @param roomId
	 * @param userId
	 * @param pageIndex
	 * @return
	 */
	@RequestMapping(value = "/deleteMember")
	public JSONMessage deleteMember(@RequestParam String roomId, @RequestParam String userId,
									@RequestParam(defaultValue = "0") int pageIndex,@RequestParam String adminUserId) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}

			if (StringUtil.isEmpty(userId) || StringUtil.isEmpty(adminUserId))
				return JSONMessage.failure("????????????");
			else {
				User user = getUserManager().getUser(Integer.valueOf(adminUserId));
				if (null != user) {
					String[] userIds = StringUtil.getStringList(userId);
					for (String strUserids : userIds) {
						Integer strUserId = Integer.valueOf(strUserids);
						getRoomManagerImplForIM().deleteMember(user, new ObjectId(roomId), strUserId);
					}
				} else {
					return JSONMessage.failure("???????????????");
				}
			}
			return JSONMessage.success();
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	@RequestMapping(value = "/deleteUser")
	public JSONMessage deleteUser(@RequestParam(defaultValue = "") String userId,HttpServletRequest request) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			if(SKBeanUtils.getIpBlackManager().checkIpBlack(request)){
				System.out.println("ip"+ NetworkUtil.getIpAddress(request)+"????????????????????????????????????.");
				return JSONMessage.failure("ip????????????????????????????????????.");
			}
			if (!StringUtil.isEmpty(userId)) {
				String[] strUserIds = StringUtil.getStringList(userId, ",");
				getUserManager().deleteUser(strUserIds);
			}
			return JSONMessage.success();
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	public User getUser() {
		Object obj = RequestContextHolder.getRequestAttributes().getAttribute(LOGIN_USER_KEY,
				RequestAttributes.SCOPE_SESSION);
		return null == obj ? null : (User) obj;
	}

	@RequestMapping(value = "/groupchat_logs", method = { RequestMethod.GET })
	public ModelAndView groupchat_logs(@RequestParam(defaultValue = "") String room_jid_id,
									   @RequestParam(defaultValue = "0") long startTime, @RequestParam(defaultValue = "0") long endTime,
									   @RequestParam(defaultValue = "0") int pageIndex, @RequestParam(defaultValue = "10") int pageSize,
									   HttpServletRequest request) {
		ModelAndView mav = new ModelAndView("groupchat_logs");
		Object historyList = getRoomManagerImplForIM().selectHistoryList(getUser().getUserId(), 0, pageIndex, pageSize);
		if (!StringUtil.isEmpty(room_jid_id)) {
			DBCollection dbCollection = dsForTigase.getDB().getCollection("shiku_muc_msgs");

			BasicDBObject q = new BasicDBObject();
			// q.put("room_jid_id", room_jid_id);
			if (0 != startTime)
				q.put("ts", new BasicDBObject("$gte", startTime));
			if (0 != endTime)
				q.put("ts", new BasicDBObject("$lte", endTime));
			long total = dbCollection.count(q);
			java.util.List<DBObject> pageData = Lists.newArrayList();

			DBCursor cursor = dbCollection.find(q).sort(new BasicDBObject("ts", -1)).skip(pageIndex * pageSize)
					.limit(pageSize);
			while (cursor.hasNext()) {
				pageData.add(cursor.next());
			}
			mav.addObject("page", new PageVO(pageData, total, pageIndex, pageSize));
		}
		mav.addObject("historyList", historyList);
		return mav;
	}

	/**
	 * ????????????
	 *
	 * @param startTime
	 * @param endTime
	 * @param room_jid_id
	 * @param page
	 * @param limit
	 * @param request
	 * @return
	 */
	@RequestMapping(value = "/groupchat_logs_all")
	public JSONMessage groupchat_logs_all(@RequestParam(defaultValue = "0") long startTime,
										  @RequestParam(defaultValue = "0") long endTime, @RequestParam(defaultValue = "") String room_jid_id,
										  @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int limit,@RequestParam(defaultValue = "") String keyWord,
										  HttpServletRequest request) {
		DBCollection dbCollection = dsForRoom.getDB().getCollection("mucmsg_" + room_jid_id);

		BasicDBObject q = new BasicDBObject();
		if (0 != startTime)
			q.put("ts", new BasicDBObject("$gte", startTime));
		if (0 != endTime)
			q.put("ts", new BasicDBObject("$lte", endTime));
		if(!StringUtil.isEmpty(keyWord))
			q.put("content", new BasicDBObject(MongoOperator.REGEX,keyWord));

		long total = dbCollection.count(q);
		List<DBObject> pageData = Lists.newArrayList();
		PageResult<DBObject> result = new PageResult<DBObject>();
		DBCursor cursor = dbCollection.find(q).sort(new BasicDBObject("ts", -1)).skip((page - 1) * limit).limit(limit);
		while (cursor.hasNext()) {
			BasicDBObject dbObj = (BasicDBObject) cursor.next();
			@SuppressWarnings("deprecation")
			String unescapeHtml3 = StringEscapeUtils.unescapeHtml3((String) dbObj.get("body"));
			JSONObject body = JSONObject.parseObject(unescapeHtml3);
			if (null != body.get("isEncrypt") && "1".equals(body.get("isEncrypt").toString())) {
				dbObj.put("isEncrypt", 1);
			} else {
				dbObj.put("isEncrypt", 0);
			}
			try {
				Map<?, ?> params = JSON.parseObject(dbObj.getString("body").replace("&quot;", "\""), Map.class);
				dbObj.put("content", params.get("content"));
				dbObj.put("fromUserName", params.get("fromUserName"));
			} catch (Exception e) {
				dbObj.put("content", "--");
			}
			pageData.add(dbObj);

		}

		result.setData(pageData);
		result.setCount(total);
		return JSONMessage.success(result);
	}

	@RequestMapping(value = "/groupchat_logs_all/del")
	public JSONMessage groupchat_logs_all_del(@RequestParam(defaultValue = "0") long startTime,
											  @RequestParam(defaultValue = "0") long endTime, @RequestParam(defaultValue = "") String msgId,
											  @RequestParam(defaultValue = "") String room_jid_id, @RequestParam(defaultValue = "0") int pageIndex,
											  @RequestParam(defaultValue = "25") int pageSize, HttpServletRequest request, HttpServletResponse response)
			throws Exception {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return JSONMessage.failure("????????????");
		}
		DBCollection dbCollection = dsForRoom.getDB().getCollection("mucmsg_" + room_jid_id);

		BasicDBObject q = new BasicDBObject();
		if (StringUtil.isEmpty(msgId))
			return JSONMessage.failure("????????????");
		else {
			String[] msgIds = StringUtil.getStringList(msgId);
			for (String strMsgId : msgIds) {
				q.put("_id", new ObjectId(strMsgId));
				dbCollection.remove(q);
			}
		}
		if (0 != startTime)
			q.put("ts", new BasicDBObject("$gte", startTime));
		if (0 != endTime)
			q.put("ts", new BasicDBObject("$lte", endTime));

		dbCollection.remove(q);
		return JSONMessage.success();
	}

	@RequestMapping(value = "/groupchatMsgDel")
	public JSONMessage groupchatMsgDel(@RequestParam(defaultValue = "") String roomJid,
									   @RequestParam(defaultValue = "0") int type) {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return JSONMessage.failure("????????????");
		}
		DBCollection dbCollection = dsForRoom.getDB().getCollection("mucmsg_" + roomJid);
		BasicDBObject q = new BasicDBObject();
		try {
			if (0 == type) {
				// ?????????????????????????????????
				long onedayNextDay = DateUtil.getOnedayNextDay(DateUtil.currentTimeSeconds(), 30, 1);
				logger.info("?????????????????????" + onedayNextDay);
				q.put("timeSend", new BasicDBObject("$lte", onedayNextDay));
				dbCollection.remove(q);
			} else if (1 == type) {
				final int num = 100000;
				int count = dbCollection.find().count();
				if (count <= num)
					throw new ServiceException("??????????????????" + num);
				// ?????????????????????????????????
				DBCursor cursor = dbCollection.find().sort(new BasicDBObject("timeSend", -1)).skip(num).limit(count);
				List<DBObject> list = cursor.toArray();
				for (DBObject dbObject : list) {
					dbCollection.remove(dbObject);
				}
				logger.info("??????" + num + "???????????????" + list.size());
			}
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getErrMessage());
		}
		return JSONMessage.success();
	}

	@RequestMapping(value = { "", "/" })
	public ModelAndView index(HttpServletRequest request, HttpServletResponse response) {
		return new ModelAndView("userStatus");
	}

	@RequestMapping(value = "/login", method = { RequestMethod.GET })
	public void openLogin(HttpServletRequest request, HttpServletResponse response) {

		String path = request.getContextPath() + "/pages/console/login.html";
		try {
			response.sendRedirect(path);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * @Description: ???????????????????????????????????????????????????????????????????????????????????????
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 **/
	@RequestMapping(value = "/login", method = { RequestMethod.POST })
	public JSONMessage login(HttpServletRequest request, HttpServletResponse response) throws Exception {
		final Integer code = 86;
		String account = request.getParameter("account");
		String password = request.getParameter("password");
		String areaCode = request.getParameter("areaCode");
		HashMap<String, Object> map = new HashMap<>();
		User user = getUserManager().getUser((StringUtil.isEmpty(areaCode) ? (code + account) : (areaCode + account)));
		if (null == user)
			return JSONMessage.failure("???????????????");
		Role userRole = SKBeanUtils.getRoleManager().getUserRole(user.getUserId(), null, 5);
		/*
		 * List<Role> userRoles =
		 * SKBeanUtils.getRoleManager().getUserRoles(user.getUserId(), null, 0);
		 * if(userRoles.size()>0 && null != userRoles){
		 * if(userRoles.contains(o)) }
		 */
		if (null == userRole)
			return JSONMessage.failure("????????????");
		if (null != userRole && -1 == userRole.getStatus())
			return JSONMessage.failure("????????????????????????");
		if(SKBeanUtils.getIpBlackManager().checkIpBlack(request)){
			System.out.println("ip"+NetworkUtil.getIpAddress(request)+"????????????????????????????????????.");
			return JSONMessage.failure("ip????????????????????????????????????.");
		}
		if (user != null && password.equals(user.getPassword())) {

			Map<String, Object> tokenMap = KSessionUtil.adminLoginSaveToken(user.getUserId().toString(), null);

			map.put("access_Token", tokenMap.get("access_Token"));
			map.put("adminId", user.getTelephone());
			map.put("account", user.getUserId() + "");
			map.put("apiKey", appConfig.getApiKey());
			map.put("role", userRole.getRole() + "");
			map.put("nickname", user.getNickname());

			map.put("registerInviteCode", SKBeanUtils.getAdminManager().getConfig().getRegisterInviteCode());
			// ????????????????????????
			// updateLastLoginTime(admin.getId(),admin.getPassword(),admin.getRole(),admin.getState(),DateUtil.currentTimeSeconds());
			updateLastLoginTime(user.getUserId());
			return JSONMessage.success(map);
		}
		return JSONMessage.failure("????????????????????????");
	}

	private void updateLastLoginTime(Integer userId) {
		Role role = new Role(userId);
		SKBeanUtils.getRoleManager().modifyRole(role);
	}

	@RequestMapping(value = "/logout")
	public void logout(HttpServletRequest request, HttpServletResponse response) throws Exception {
		request.getSession().removeAttribute(LOGIN_USER_KEY);
		KSessionUtil.removeAdminToken(ReqUtil.getUserId());
		response.sendRedirect("/console/login");
	}

	@RequestMapping(value = "/pushToAll")
	public void pushToAll(HttpServletResponse response, @RequestParam int fromUserId, @RequestParam String body) {

		MessageBean mb = JSON.parseObject(body, MessageBean.class);
		mb.setFromUserId(fromUserId + "");
		mb.setTimeSend(DateUtil.currentTimeSeconds());
		mb.setMsgType(0);
		mb.setMessageId(StringUtil.randomUUID());
		ThreadUtil.executeInThread(new Callback() {

			@Override
			public void execute(Object obj) {
				DBCursor cursor = dsForRW.getDB().getCollection("user").find(null, new BasicDBObject("_id", 1))
						.sort(new BasicDBObject("_id", -1));
				while (cursor.hasNext()) {
					BasicDBObject dbObj = (BasicDBObject) cursor.next();
					int userId = dbObj.getInt("_id");
					try {
						mb.setToUserId(String.valueOf(userId));
						KXMPPServiceImpl.getInstance().send(mb);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		});
		try {
			response.setContentType("text/html; charset=UTF-8");
			PrintWriter writer = response.getWriter();
			writer.write(
					"<script type='text/javascript'>alert('\u6279\u91CF\u53D1\u9001\u6D88\u606F\u5DF2\u5B8C\u6210\uFF01');window.location.href='/pages/qf.jsp';</script>");
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@RequestMapping(value = "/roomList")
	public JSONMessage roomList(@RequestParam(defaultValue = "") String keyWorld,
								@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "15") int limit,@RequestParam(defaultValue = "0") int leastNumbers) {

		PageResult<Room> result = new PageResult<Room>();

		Query<Room> query = dsForRoom.createQuery(Room.class);

		if (!StringUtil.isEmpty(keyWorld)) {
			query.criteria("name").containsIgnoreCase(keyWorld);
		}
		if(leastNumbers > 0)
			query.field("userSize").greaterThan(leastNumbers);

		result.setData(query.order("-createTime").asList(getRoomManagerImplForIM().pageFindOption(page, limit, 1)));
		result.setCount(query.count());

		return JSONMessage.success(result);
	}

	@RequestMapping(value = "/getRoomMember")
	public JSONMessage getRoom(@RequestParam(defaultValue = "") String roomId) {
		Room room = SKBeanUtils.getRoomManagerImplForIM().consoleGetRoom(new ObjectId(roomId));
		return JSONMessage.success(null, room);
	}

	/**
	 * ?????????????????????
	 *
	 * @param pageIndex
	 * @param pageSize
	 * @param room_jid_id
	 * @return
	 */
	@RequestMapping(value = "/roomMsgDetail")
	public JSONMessage roomDetail(@RequestParam(defaultValue = "0") int page,
								  @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "") String room_jid_id) {

		DBCollection dbCollection = dsForRoom.getDB().getCollection("mucmsg_" + room_jid_id);
		BasicDBObject q = new BasicDBObject();
		q.put("contentType", 1);
		if (!StringUtil.isEmpty(room_jid_id))
			q.put("room_jid_id", room_jid_id);
		logger.info("?????? ?????????" + dbCollection.find(q).count());
		long total = dbCollection.count(q);
		List<DBObject> pageData = Lists.newArrayList();
		DBCursor cursor = dbCollection.find(q).sort(new BasicDBObject("_id", 1)).skip((page - 1) * limit).limit(limit);
		while (cursor.hasNext()) {
			BasicDBObject dbObj = (BasicDBObject) cursor.next();
			try {
				Map<?, ?> params = JSON.parseObject(dbObj.getString("body").replace("&quot;", "\""), Map.class);
				dbObj.put("content", params.get("content"));
				dbObj.put("fromUserName", params.get("fromUserName"));
			} catch (Exception e) {
				dbObj.put("content", "--");
			}
			pageData.add(dbObj);
		}
		PageResult<DBObject> result = new PageResult<DBObject>();
		result.setData(pageData);
		result.setCount(total);
		return JSONMessage.success(result);
	}

	/**
	 * ??????????????????????????????
	 *
	 * @param userId
	 * @param startDate
	 * @param endDate
	 * @param page
	 * @param limit
	 * @return
	 */
	@RequestMapping(value = "/getGiftList")
	public JSONMessage get(@RequestParam Integer userId, @RequestParam(defaultValue = "") String startDate,
						   @RequestParam(defaultValue = "") String endDate, @RequestParam(defaultValue = "0") int page,
						   @RequestParam(defaultValue = "10") int limit) {
		try {
			PageResult<Givegift> result = SKBeanUtils.getLiveRoomManager().getGiftList(userId, startDate, endDate, page,
					limit);
			return JSONMessage.success(result);
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}

	}

	@RequestMapping(value = "/userList")
	public JSONMessage userList(@RequestParam(defaultValue = "0") int page,
								@RequestParam(defaultValue = "10") int limit, @RequestParam(defaultValue = "") String onlinestate,
								@RequestParam(defaultValue = "") String keyWorld, @RequestParam(defaultValue = "") String startDate,
								@RequestParam(defaultValue = "") String endDate, Integer userType) {
		Query<User> query = getUserManager().createQuery();

		if (!StringUtil.isEmpty(keyWorld)) {
			// Integer ?????????2147483647
			boolean flag = NumberUtil.isNum(keyWorld);
			if(flag){
				Integer length = keyWorld.length();
				if(length > 9){
					query.or(query.criteria("nickname").containsIgnoreCase(keyWorld),
							query.criteria("telephone").containsIgnoreCase(keyWorld));
				}else{
					query.or(query.criteria("nickname").containsIgnoreCase(keyWorld),
							query.criteria("telephone").containsIgnoreCase(keyWorld),
							query.criteria("_id").equal(Integer.valueOf(keyWorld)));
				}
			}else{
				query.or(query.criteria("nickname").containsIgnoreCase(keyWorld),
						query.criteria("telephone").containsIgnoreCase(keyWorld));
			}
		}
		if (!StringUtil.isEmpty(onlinestate)) {
			query.filter("onlinestate", Integer.valueOf(onlinestate));
		}
		if (null != userType) {
			query.filter("userType", userType);
		}
		if(!StringUtil.isEmpty(startDate) && !StringUtil.isEmpty(endDate)){
			long startTime = 0; //?????????????????????
			long endTime = 0; //?????????????????????,?????????????????????
			startTime = StringUtil.isEmpty(startDate) ? 0 :DateUtil.toDate(startDate).getTime()/1000;
			endTime = StringUtil.isEmpty(endDate) ? DateUtil.currentTimeSeconds() : DateUtil.toDate(endDate).getTime()/1000;
			long formateEndtime = DateUtil.getOnedayNextDay(endTime,1,0);
			query.field("createTime").greaterThan(startTime).field("createTime").lessThanOrEq(formateEndtime);
		}
		// ???????????????
		List<User> pageData = query.order("-createTime").asList(getUserManager().pageFindOption(page, limit, 1));
		pageData.forEach(userInfo -> {
			Query<UserLoginLog> loginLog = SKBeanUtils.getDatastore().createQuery(UserLoginLog.class).field("userId")
					.equal(userInfo.getUserId());
			if (null != loginLog.get())
				userInfo.setLoginLog(loginLog.get().getLoginLog());
		});
		PageResult<User> result = new PageResult<User>();
		result.setData(pageData);
		result.setCount(query.count());
		return JSONMessage.success(result);
	}

	/**
	 * ??????????????????
	 *
	 * @param pageIndex
	 * @param pageSize
	 * @return
	 */
	@SuppressWarnings("deprecation")
	@RequestMapping(value = "/newRegisterUser")
	public JSONMessage newRegisterUser(@RequestParam(defaultValue = "0") int pageIndex,
									   @RequestParam(defaultValue = "10") int pageSize) {
		Query<User> query = getUserManager().createQuery();
		long total = query.count();
		List<User> pageData = query.order("-createTime").offset(pageIndex * pageSize).limit(pageSize).asList();
		PageVO page = new PageVO(pageData, total, pageIndex, pageSize);
		return JSONMessage.success(null, page);
	}

	/**
	 * ????????????
	 *
	 * @param userId
	 * @return
	 */
	@RequestMapping(value = "/restPwd")
	public JSONMessage restPwd(@RequestParam(defaultValue = "0") Integer userId) {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return JSONMessage.failure("????????????");
		}
		if (0 < userId)
			getUserManager().resetPassword(userId, Md5Util.md5Hex("123456"));
		return JSONMessage.success();
	}

	/**
	 * ??????admin??????
	 *
	 * @param userId
	 * @return
	 */
	@RequestMapping(value = "/updateAdminPassword")
	public JSONMessage updatePassword(@RequestParam(defaultValue="") String oldPassword,@RequestParam(defaultValue = "0") Integer userId, String password) {
		try {
			User user = SKBeanUtils.getUserManager().getUser(userId);
			if(user.getPassword().equals(oldPassword)){
				getUserManager().resetPassword(userId, password);
				return JSONMessage.success();
			}else{
				return JSONMessage.failure("?????????????????????");
			}

		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ??????????????????
	 * @param userId
	 * @param password
	 * @return
	 */
	@RequestMapping(value = "/updateUserPassword")
	public JSONMessage updateUserPassword(@RequestParam(defaultValue = "0") Integer userId, String password){
		try {
			getUserManager().resetPassword(userId, password);
			return JSONMessage.success();
		} catch (Exception e) {

			return JSONMessage.failure(e.getMessage());
		}

	}

	/**
	 * ????????????
	 *
	 * @param userId
	 * @return
	 */
	@RequestMapping(value = "/friendsList")
	public JSONMessage friendsList(@RequestParam(defaultValue = "0") Integer userId,
								   @RequestParam(defaultValue = "0") Integer toUserId, @RequestParam(defaultValue = "0") int status,
								   @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int limit) {
		try {
			PageResult<Friends> friendsList = SKBeanUtils.getFriendsManager().consoleQueryFollow(userId, toUserId,
					status, page, limit);
			return JSONMessage.success(friendsList);
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ????????????
	 *
	 * @param userId
	 * @param toUserIds
	 * @return
	 */
	@RequestMapping("/deleteFriends")
	public JSONMessage deleteFriends(@RequestParam(defaultValue = "0") Integer userId,
									 @RequestParam(defaultValue = "") String toUserIds,@RequestParam(defaultValue = "")Integer adminUserId) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}

			if (StringUtil.isEmpty(toUserIds))
				JSONMessage.failure("????????????");
			else {
				String[] toUserId = StringUtil.getStringList(toUserIds, ",");

				SKBeanUtils.getFriendsManager().getFriends(userId, toUserId);
				/*
				 * Friends friends =
				 * SKBeanUtils.getFriendsManager().getFriends(userId, toUserId);
				 * if (null == friends) return JSONMessage.failure("????????????????????????!");
				 */
				SKBeanUtils.getFriendsManager().consoleDeleteFriends(userId, adminUserId,toUserId);
			}
			return JSONMessage.success();

		} catch (ServiceException e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	@RequestMapping(value = "/getUpdateUser")
	public JSONMessage updateUser(@RequestParam(defaultValue = "0") Integer userId) {
		User user = null;
		if (0 == userId)
			user = new User();
		else {
			user = getUserManager().getUser(userId);
			List<Integer> userRoles = SKBeanUtils.getRoleManager().getUserRoles(userId);
			System.out.println("???????????????" + JSONObject.toJSONString(userRoles));
			if (null != userRoles) {
				for (Integer role : userRoles) {
					if (role.equals(2)) {
						user.setUserType(2);
					} else {
						user.setUserType(0);
					}
				}
			}
		}
		return JSONMessage.success(user);
	}

	@RequestMapping(value = "/updateUser")
	public JSONMessage saveUserMsg(HttpServletRequest request, HttpServletResponse response,
								   @RequestParam(defaultValue = "0") Integer userId, @ModelAttribute UserExample example) throws Exception {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return JSONMessage.failure("????????????");
		}

		if (!StringUtil.isEmpty(example.getTelephone())) {
			example.setPhone(example.getTelephone());
			example.setTelephone(example.getAreaCode() + example.getTelephone());
		}
		// ??????????????????(?????????????????????????????????????????????????????????)
		if (!StringUtil.isEmpty(example.getPassword()))
			example.setPassword(DigestUtils.md5Hex(example.getPassword()));

		Object uid;
		// ??????????????????
		if (0 == userId) {
			Map<String, Object> saveUser = getUserManager().registerIMUser(example,request);
			uid = saveUser.get("userId");
		} else {
			getUserManager().updateUser(userId, example);
			uid = userId;
			// ???????????????????????????toUserType
//			SKBeanUtils.getRoleManager().updateFriend(userId, example.getUserType());
		}

		return JSONMessage.success(uid);
	}

	/**
	 * @Description:??????????????????
	 * @param pageIndex
	 * @param pageSize
	 * @return
	 **/
	@RequestMapping("/redPacketList")
	public JSONMessage getRedPacketList(@RequestParam(defaultValue = "") String userName,
										@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int limit,@RequestParam(defaultValue = "") String redPacketId) {
		try {
			PageResult<RedPacket> result = SKBeanUtils.getRedPacketManager().getRedPacketList(userName, page, limit,redPacketId);
			return JSONMessage.success(result);
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getErrMessage());
		}
	}

	@RequestMapping("/receiveWater")
	public JSONMessage receiveWater(@RequestParam(defaultValue = "") String redId,
									@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int limit) {
		try {
			PageResult<RedReceive> result = SKBeanUtils.getRedPacketManager().receiveWater(redId, page, limit);
			return JSONMessage.success(result);
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getErrMessage());
		}
	}

	@RequestMapping(value = "/editRoom")
	public ModelAndView addRomm(HttpServletRequest request, HttpServletResponse response,
								@RequestParam(defaultValue = "") String id) throws Exception {
		ModelAndView mav = new ModelAndView("editRoom");
		if (StringUtil.isEmpty(id)) {
			mav.addObject("o", new Room());
			mav.addObject("action", "addRoom");
		} else {
			mav.addObject("o", getRoomManagerImplForIM().getRoom(parse(id)));
			mav.addObject("action", "updateRoom");
		}

		return mav;
	}

	@RequestMapping(value = "/addRoom")
	public JSONMessage addRomm(HttpServletRequest request, HttpServletResponse response, @ModelAttribute Room room,
							   @RequestParam(defaultValue = "") String ids) throws Exception {
		List<Integer> idList = StringUtil.isEmpty(ids) ? null : JSON.parseArray(ids, Integer.class);
		if (null == room.getId()) {
			User user = getUserManager().getUser(room.getUserId());
			String jid = SKBeanUtils.getXmppService().createMucRoom(user.getPassword(), user.getUserId().toString(),
					room.getName(),null, room.getSubject(), room.getDesc());
			room.setJid(jid);
			getRoomManagerImplForIM().add(user, room, idList);
		}

		return JSONMessage.success();
	}

	@RequestMapping(value = "/updateRoom")
	public JSONMessage updateRoom(HttpServletRequest request, HttpServletResponse response,
								  @ModelAttribute RoomVO roomVo) throws Exception {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}

			User user = getUserManager().get(roomVo.getUserId());
			if (null == user)
				return JSONMessage.failure("????????????");
			getRoomManagerImplForIM().update(user, roomVo, 1, 1);
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getMessage());
		}

		return JSONMessage.success();
	}

	@RequestMapping(value = "/roomUserManager")
	public JSONMessage roomUserManager(@RequestParam(defaultValue = "0") int page,
									   @RequestParam(defaultValue = "10") int limit, @RequestParam(defaultValue = "") String id,@RequestParam(defaultValue = "") String keyWorld) throws Exception {

		try {
			PageResult<Member> result = null;
			if (!StringUtil.isEmpty(id)) {
				result = getRoomManagerImplForIM().getMemberListByPage(new ObjectId(id), page, limit,keyWorld);
			}
			return JSONMessage.success(result);
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}

	}

	@RequestMapping(value = "/roomMemberList")
	public JSONMessage roomMemberList(@RequestParam String id) {
		Object data = getRoomManagerImplForIM().getMemberList(new ObjectId(id), "");
		return JSONMessage.success(null, data);
	}

	@RequestMapping(value = "/sendMessage", method = { RequestMethod.POST })
	public ModelAndView sendMssage(@RequestParam String body, Integer from, Integer to, Integer count) {
		ModelAndView mav = new ModelAndView("qf");
		try {

			logger.info("body=======>  " + body);
			// String msg = new String(body.getBytes("iso8859-1"),"utf-8");
			if (null == from) {
				List<Friends> uList = SKBeanUtils.getFriendsManager().queryFriendsList(to, 0, 0, count);
				new Thread(new Runnable() {

					@Override
					public void run() {
						User user = null;
						MessageBean messageBean = null;
						;
						for (Friends friends : uList) {
							try {
								user = getUserManager().getUser(friends.getToUserId());
								messageBean = new MessageBean();
								messageBean.setType(1);
								messageBean.setContent(body);
								messageBean.setFromUserId(user.getUserId() + "");
								messageBean.setFromUserName(user.getNickname());
								messageBean.setMessageId(UUID.randomUUID().toString());
								messageBean.setToUserId(to.toString());
								messageBean.setMsgType(0);
								messageBean.setMessageId(StringUtil.randomUUID());
								KXMPPServiceImpl.getInstance().send(messageBean);
							} catch (Exception e) {
								e.printStackTrace();
							}
							;
						}
					}
				}).start();
			} else {
				new Thread(new Runnable() {

					@Override
					public void run() {
						User user = getUserManager().get(from);
						MessageBean messageBean = new MessageBean();
						messageBean.setContent(body);
						messageBean.setFromUserId(user.getUserId().toString());
						messageBean.setFromUserName(user.getNickname());
						messageBean.setToUserId(to.toString());
						messageBean.setMsgType(0);
						messageBean.setMessageId(StringUtil.randomUUID());
						KXMPPServiceImpl.getInstance().send(messageBean);
					}
				}).start();
			}
			return mav;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return mav;
	}

	@RequestMapping(value = "/addAllUser")
	public JSONMessage updateTigaseDomain() throws Exception {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return JSONMessage.failure("????????????");
		}

		Cursor attach = dsForRW.getDB().getCollection("user").find();
		String userId = "";
		String password = "";
		while (attach.hasNext()) {
			DBObject fileobj = attach.next();
			DBObject ref = new BasicDBObject();
			ref.put("user_id", fileobj.get("_id") + "@" + SKBeanUtils.getXMPPConfig().getServerName());
			DBObject obj = dsForTigase.getDB().getCollection("tig_users").findOne(ref);
			userId = fileobj.get("_id").toString().replace(".", "0");
			password = fileobj.get("password").toString();
			if (null != obj) {
				logger.info(fileobj.get("_id").toString() + "  ?????????");
			} else {
				String user_id = userId + "@" + SKBeanUtils.getXMPPConfig().getServerName();
				BasicDBObject jo = new BasicDBObject();
				jo.put("_id", generateId(user_id));
				jo.put("user_id", user_id);
				jo.put("domain", SKBeanUtils.getXMPPConfig().getServerName());
				jo.put("password", password);
				jo.put("type", "shiku");
				dsForTigase.getDB().getCollection("tig_users").save(jo);
				logger.info(user_id + "  ?????????Tigase???" + SKBeanUtils.getXMPPConfig().getServerName());
			}
		}
		return JSONMessage.success();
	}

	private byte[] generateId(String username) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		return md.digest(username.getBytes());
	}

	@RequestMapping(value = "/initSysUser")
	public JSONMessage initSysUser(@RequestParam(defaultValue = "0") int userId) throws Exception {
		if (0 < userId) {
			getUserManager().addUser(userId, String.valueOf(userId));

			KXMPPServiceImpl.getInstance().registerSystemNo(String.valueOf(userId),
					Md5Util.md5Hex(String.valueOf(userId)));
		} else {
			// SKBeanUtils.getAdminManager().initSystemNo();
		}
		return JSONMessage.success();
	}

	/**
	 * ???????????????
	 *
	 * @param name
	 * @param nickName
	 * @param userId
	 * @param page
	 * @param limit
	 * @param status
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/liveRoomList")
	public JSONMessage liveRoomList(@RequestParam(defaultValue = "") String name,
									@RequestParam(defaultValue = "") String nickName, @RequestParam(defaultValue = "0") Integer userId,
									@RequestParam(defaultValue = "0") Integer page, @RequestParam(defaultValue = "10") Integer limit,
									@RequestParam(defaultValue = "-1") Integer status) throws Exception {
		PageResult<LiveRoom> result = new PageResult<LiveRoom>();
		try {
			result = getLiveRoomManager().findConsoleLiveRoomList(name, nickName, userId, page, limit, status, 1);
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
		return JSONMessage.success(result);
	}

	@RequestMapping(value = "/addLiveRoom", method = { RequestMethod.GET })
	public ModelAndView addLiveRoom() {
		ModelAndView mav = new ModelAndView("addliveRoom");
		mav.addObject("o", new LiveRoom());
		return mav;
	}

	/**
	 * ?????????????????????
	 *
	 * @param request
	 * @param response
	 * @param liveRoom
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/saveNewLiveRoom", method = { RequestMethod.POST })
	public JSONMessage saveNewLiveRoom(HttpServletRequest request, HttpServletResponse response, LiveRoom liveRoom)
	{
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			User user = getUserManager().getUser(liveRoom.getUserId());
			String jid = SKBeanUtils.getXmppService().createMucRoom(user.getPassword(), user.getUserId().toString(),
					liveRoom.getName(),null, liveRoom.getNotice(), liveRoom.getNotice());
			liveRoom.setJid(jid);
			getLiveRoomManager().createLiveRoom(liveRoom);
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}

		return JSONMessage.success();
	}

	/**
	 * ???????????????
	 *
	 * @param liveRoomId
	 * @return
	 */
	@RequestMapping(value = "/deleteLiveRoom", method = { RequestMethod.POST })
	public JSONMessage deleteLiveRoom(@RequestParam String liveRoomId) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}

			DBCollection dbCollection = dsForTigase.getCollection(LiveRoom.class);
			getLiveRoomManager().deleteLiveRoom(new ObjectId(liveRoomId));
			/* liveRoomManager.deleteLiveRoom(new ObjectId(liveRoomId)); */
			dbCollection.remove(new BasicDBObject("_id", new ObjectId(liveRoomId)));
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * ????????????????????????
	 *
	 * @param liveRoomId
	 * @param currentState
	 * @return
	 */
	@RequestMapping(value = "/operationLiveRoom")
	public JSONMessage operationLiveRoom(@RequestParam String liveRoomId,
										 @RequestParam(defaultValue = "0") int currentState) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}

			getLiveRoomManager().operationLiveRoom(new ObjectId(liveRoomId), currentState);
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ?????????????????????
	 *
	 * @param pageIndex
	 * @param name
	 * @param nickName
	 * @param userId
	 * @param pageSize
	 * @param roomId
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/liveRoomUserManager")
	public JSONMessage liveRoomManager(@RequestParam(defaultValue = "0") int pageIndex,
									   @RequestParam(defaultValue = "") String name, @RequestParam(defaultValue = "") String nickName,
									   @RequestParam(defaultValue = "0") Integer userId, @RequestParam(defaultValue = "10") int pageSize,
									   @RequestParam(defaultValue = "") String roomId) throws Exception {
		List<LiveRoomMember> pageData = Lists.newArrayList();
		pageData = getLiveRoomManager().findLiveRoomMemberList(new ObjectId(roomId));
		PageResult<LiveRoomMember> result = new PageResult<LiveRoomMember>();
		result.setData(pageData);
		result.setCount(pageData.size());
		return JSONMessage.success(result);
	}

	/**
	 * ?????????????????????
	 *
	 * @param userId
	 * @param liveRoomId
	 * @param response
	 * @param pageIndex
	 * @return
	 */
	@RequestMapping(value = "/deleteRoomUser")
	public JSONMessage deleteliveRoomUserManager(@RequestParam Integer userId,
												 @RequestParam(defaultValue = "") String liveRoomId, HttpServletResponse response,
												 @RequestParam(defaultValue = "0") int pageIndex) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			getLiveRoomManager().kick(userId, new ObjectId(liveRoomId));
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * ??????
	 *
	 * @param userId
	 * @param state
	 * @param roomId
	 * @return
	 */
	@RequestMapping(value = "/shutup")
	public JSONMessage shutup(@RequestParam Integer userId, @RequestParam int state, @RequestParam ObjectId roomId) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}

			LiveRoomMember shutup = getLiveRoomManager().shutup(state, userId, roomId);
			System.out.println(JSONObject.toJSONString(shutup));
			return JSONMessage.success(shutup);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * ??????
	 */
	@RequestMapping(value = "/banplay")
	public void ban() {
		try {

		} catch (Exception e) {
			// TODO: handle exception
		}
	}

	/**
	 * ????????????
	 *
	 * @param name
	 * @param pageIndex
	 * @param pageSize
	 * @return
	 */
	@SuppressWarnings("unchecked")
	@RequestMapping(value = "/giftList")
	public JSONMessage giftList(@RequestParam(defaultValue = "") String name,
								@RequestParam(defaultValue = "0") int pageIndex, @RequestParam(defaultValue = "10") int pageSize) {
		try {
			Map<String, Object> pageData = getLiveRoomManager().consolefindAllgift(name, pageIndex, pageSize);
			if (null != pageData) {
				long total = (long) pageData.get("total");
				List<Gift> giftList = (List<Gift>) pageData.get("data");
				return JSONMessage.success(null, new PageVO(giftList, total, pageIndex, pageSize, total));
			}
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
		return null;
	}

	/**
	 * ????????????
	 *
	 * @return
	 */
	@RequestMapping(value = "/add/gift", method = { RequestMethod.GET })
	public ModelAndView getAddGiftPage() {
		ModelAndView mav = new ModelAndView("addGift");
		mav.addObject("o", new LiveRoom());
		return mav;
	}

	/**
	 * ????????????
	 *
	 * @param request
	 * @param response
	 * @param name
	 * @param photo
	 * @param price
	 * @param type
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/add/gift", method = { RequestMethod.POST })
	public JSONMessage addGift(HttpServletRequest request, HttpServletResponse response, @RequestParam String name,
							   @RequestParam String photo, @RequestParam double price, @RequestParam int type) throws IOException {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			getLiveRoomManager().addGift(name, photo, price, type);
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ????????????
	 *
	 * @param giftId
	 * @return
	 */
	@RequestMapping(value = "/delete/gift")
	public JSONMessage deleteGift(@RequestParam String giftId) {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return JSONMessage.failure("????????????");
		}
		getLiveRoomManager().deleteGift(new ObjectId(giftId));
		return JSONMessage.success();

	}

	/**
	 * ??????????????????
	 *
	 * @param keyword
	 * @param pageIndex
	 * @param pageSize
	 * @return
	 */
	@RequestMapping(value = "/messageList")
	public JSONMessage messageList(String keyword, @RequestParam(defaultValue = "0") int pageIndex,
								   @RequestParam(defaultValue = "10") int pageSize) {
		try {
			long totalNum = 0;
			Map<Long, List<ErrorMessage>> errorMessage = SKBeanUtils.getErrorMessageManage().findErrorMessage(keyword,
					pageIndex, pageSize);
			if (null != errorMessage.keySet()) {
				for (Long total : errorMessage.keySet()) {
					if (total == 0) {
						return JSONMessage.success("??????????????????");
					}
					totalNum = total;
				}
			}
			return JSONMessage.success(null,
					new PageVO(errorMessage.get(totalNum), totalNum, pageIndex, pageSize, totalNum));
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ??????????????????
	 *
	 * @param errorMessage
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/saveErrorMessage")
	public JSONMessage saveErrorMessage(ErrorMessage errorMessage) throws IOException {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			if (null == errorMessage)
				return JSONMessage.failure("????????????");
			return SKBeanUtils.getErrorMessageManage().saveErrorMessage(errorMessage);
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ??????????????????
	 *
	 * @param errorMessage
	 * @param id
	 * @return
	 */
	@RequestMapping(value = "/messageUpdate", method = { RequestMethod.POST })
	public JSONMessage messageUpdate(ErrorMessage errorMessage, String id) {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return JSONMessage.failure("????????????");
		}
		if (null == errorMessage)
			return JSONMessage.failure("??????????????? errorMessage " + errorMessage);
		ErrorMessage data = SKBeanUtils.getErrorMessageManage().updataErrorMessage(id, errorMessage);
		if (null == data)
			return JSONMessage.failure("????????????????????????");
		else
			return JSONMessage.success("????????????????????????", data);
	}

	/**
	 * ??????????????????
	 *
	 * @param code
	 * @return
	 */
	@RequestMapping(value = "/deleteErrorMessage")
	public JSONMessage deleteErrorMessage(@RequestParam(defaultValue = "") String code) {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return JSONMessage.failure("????????????");
		}
		if (StringUtil.isEmpty(code))
			return JSONMessage.failure("????????????,code: " + code);
		boolean falg = SKBeanUtils.getErrorMessageManage().deleteErrorMessage(code);
		if (!falg)
			return JSONMessage.failure("????????????????????????");
		else
			return JSONMessage.success();
	}

	/**
	 * ???????????????
	 *
	 * @param word
	 * @param pageIndex
	 * @param pageSize
	 * @return
	 */
	@RequestMapping(value = "/keywordfilter")
	public JSONMessage keywordfilter(@RequestParam(defaultValue = "") String word,
									 @RequestParam(defaultValue = "0") int pageIndex, @RequestParam(defaultValue = "10") int pageSize) {
		Query<KeyWord> query = dsForRW.createQuery(KeyWord.class);
		if (!StringUtil.isEmpty(word)) {
			query.filter("word", word);
		}
		List<KeyWord> list = null;
		long total = 0;
		list = query.order("-createTime").asList(new FindOptions().skip(pageIndex * pageSize).limit(pageSize));
		total = query.count();
		return JSONMessage.success(null, new PageVO(list, total, pageIndex, pageSize, total));
	}

	@RequestMapping("/sendMsg")
	public JSONMessage sendMsg(@RequestParam(defaultValue = "") String jidArr,
							   @RequestParam(defaultValue = "1") int userId, @RequestParam(defaultValue = "1") int type,
							   @RequestParam(defaultValue = "") String content, String objectId, @RequestParam(defaultValue = "0") int fileSize) {
		// ????????????
		/*byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return JSONMessage.failure("????????????");
		}*/
		String[] split = jidArr.split(",");

		SKBeanUtils.getRoomManagerImplForIM().sendMsgToRooms(split, userId, type, content, objectId, fileSize);

		return JSONMessage.success();

	}

	/**
	 * ???????????????
	 *
	 * @param response
	 * @param id
	 * @param word
	 * @throws IOException
	 */
	@RequestMapping(value = "/addkeyword", method = { RequestMethod.POST })
	public void addkeyword(HttpServletResponse response, @RequestParam(defaultValue = "") String id,
						   @RequestParam String word) throws IOException {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return ;
		}
		KeyWord keyword = null;
		if (StringUtil.isEmpty(id)) {
			keyword = new KeyWord();
			keyword.setWord(word);
			keyword.setCreateTime(DateUtil.currentTimeSeconds());
			dsForRW.save(keyword);
		} else {
			Query<KeyWord> query = dsForRW.createQuery(KeyWord.class);
			UpdateOperations<KeyWord> ops = dsForRW.createUpdateOperations(KeyWord.class);
			ops.set("word", word);
			ops.set("createTime", DateUtil.currentTimeSeconds());
			dsForRW.update(query, ops);
		}
		response.sendRedirect("/console/keywordfilter");
	}

	/**
	 * ???????????????
	 *
	 * @param response
	 * @param id
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/deletekeyword", method = { RequestMethod.POST })
	public JSONMessage deletekeyword(HttpServletResponse response, @RequestParam String id) throws IOException {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return JSONMessage.failure("????????????");
		}
		Query<KeyWord> query = dsForRW.createQuery(KeyWord.class);
		query.field("_id").equal(new ObjectId(id));
		dsForRW.delete(query.get());
		return JSONMessage.success();
	}

	/**
	 * ??????????????????
	 *
	 * @param request
	 * @param response
	 * @param startTime
	 * @param endTime
	 * @param room_jid_id
	 * @throws Exception
	 */
	@RequestMapping(value = "/deleteMsgGroup", method = { RequestMethod.POST })
	public void deleteMsgGroup(HttpServletRequest request, HttpServletResponse response,
							   @RequestParam(defaultValue = "0") long startTime, @RequestParam(defaultValue = "0") long endTime,
							   @RequestParam(defaultValue = "") String room_jid_id) throws Exception {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return ;
		}
		if (room_jid_id != null) {
			DBCollection dbCollection = dsForRoom.getDB().getCollection("mucmsg_" + room_jid_id);
			BasicDBObject query = new BasicDBObject();
			if (0 != startTime) {
				query.put("ts", new BasicDBObject("$gte", startTime));
			}
			if (0 != endTime) {
				query.put("ts", new BasicDBObject("$gte", endTime));
			}
			DBCursor cursor = dbCollection.find(query);
			if (cursor.size() > 0) {

				BasicDBObject dbObj = (BasicDBObject) cursor.next();
				// ???????????????

				Map<String, Object> body = JSON.parseObject(dbObj.getString("body").replace("&quot;", "\""), Map.class);
				int contentType = (int) body.get("type");

				if (contentType == 2 || contentType == 3 || contentType == 5 || contentType == 6 || contentType == 7
						|| contentType == 9) {
					String paths = (String) body.get("content");
					Query<Emoji> que = dsForRW.createQuery(Emoji.class);
					List<Emoji> list = que.asList();
					for (int i = 0; i < list.size(); i++) {
						if (list.get(i).getUrl() == paths) {
							return;
						} else {
							try {
								// ?????????????????????????????????????????????
								ConstantUtil.deleteFile(paths);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}
				}
				dbCollection.remove(query); // ?????????????????????????????????
			}
		} else {
			List<String> jidList = dsForRoom.getCollection(Room.class).distinct("jid", new BasicDBObject());

			for (int j = 0; j < jidList.size(); j++) {
				DBCollection dbCollection = dsForRoom.getDB().getCollection("mucmsg_" + jidList.get(j));
				BasicDBObject query = new BasicDBObject();
				if (0 != startTime) {
					query.put("ts", new BasicDBObject("$gte", startTime));
				}
				if (0 != endTime) {
					query.put("ts", new BasicDBObject("$gte", endTime));
				}
				DBCursor cursor = dbCollection.find(query);
				if (cursor.size() > 0) {
					BasicDBObject dbObj = (BasicDBObject) cursor.next();
					// ???????????????
					Map<String, Object> body = JSON.parseObject(dbObj.getString("body").replace("&quot;", "\""),
							Map.class);
					int contentType = (int) body.get("type");

					if (contentType == 2 || contentType == 3 || contentType == 5 || contentType == 6 || contentType == 7
							|| contentType == 9) {
						String paths = (String) body.get("content");
						Query<Emoji> que = dsForRW.createQuery(Emoji.class);
						List<Emoji> list = que.asList();
						for (int i = 0; i < list.size(); i++) {
							if (list.get(i).getUrl() == paths) {
								return;
							} else {
								try {
									// ?????????????????????????????????????????????
									ConstantUtil.deleteFile(paths);
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						}
					}
					dbCollection.remove(query); // ?????????????????????????????????
				}
			}

		}

		referer(response, "/console/groupchat_logs_all?room_jid_id=" + room_jid_id, 0);
	}

	/**
	 * ????????????????????????
	 *
	 * @param userNum
	 *            ?????????????????????
	 * @param roomId
	 */
	@RequestMapping(value = "/autoCreateUser")
	public JSONMessage autoCreateUser(@RequestParam(defaultValue = "0") int userNum,
									  @RequestParam(defaultValue = "") String roomId) {
		try {
			if (userNum > 0)
				getUserManager().autoCreateUserOrRoom(userNum, roomId);
			else
				return JSONMessage.failure("????????????1???");
			return JSONMessage.success();
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getMessage());
		}

	}

	/**
	 * ???????????????????????????????????? excel
	 */
	@RequestMapping(value = "/exportData")
	public JSONMessage exportData(HttpServletRequest request, HttpServletResponse response,
								  @RequestParam(defaultValue = "3") short userType) {
		String fileName = "users.xlsx";

		int maxNum = 30000; // ????????????3????????????
		short onlinestate = -1;

		List<User> userList = getUserManager().findUserList(0, maxNum, "", onlinestate, userType);

		String name = "???????????????????????????";
		List<String> titles = Lists.newArrayList();
		titles.add("userId");
		titles.add("nickname");
		titles.add("telephone");
		titles.add("password");
		titles.add("sex");
		titles.add("createTime");

		List<Map<String, Object>> values = Lists.newArrayList();
		for (User user : userList) {
			Map<String, Object> map = Maps.newHashMap();
			map.put("userId", user.getUserId());
			map.put("nickname", user.getNickname());
			map.put("telephone", user.getTelephone());
			map.put("password", user.getUserType() == 3 ? "" + (user.getUserId() - 1000) / 2 : user.getPassword());
			map.put("sex", user.getSex() == 1 ? "???" : "???");
			map.put("createTime", Calendar.getInstance());
			values.add(map);
		}

		Workbook workBook = ExcelUtil.generateWorkbook(name, "xlsx", titles, values);
		try {
			response.reset();
			response.setHeader("Content-Disposition",
					"attachment;filename=" + new String(fileName.getBytes(), "utf-8"));
			ServletOutputStream out = response.getOutputStream();
			workBook.write(out);
			// ?????????????????????
			out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return JSONMessage.success();
	}

	@RequestMapping(value = "/exportExcelByFriends")
	public JSONMessage exportExcelByFriends(HttpServletRequest request, HttpServletResponse response,
											@RequestParam(defaultValue = "0") Integer userId) {
		try {
			Workbook workBook = SKBeanUtils.getFriendsManager().exprotExcelFriends(userId, request, response);
			ServletOutputStream out = response.getOutputStream();
			workBook.write(out);
			// ?????????????????????
			out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return JSONMessage.success();
	}

	/**
	 * @Description:???????????????
	 * @param request
	 * @param response
	 * @param roomId
	 * @return
	 **/
	@RequestMapping(value = "/exportExcelByGroupMember")
	public JSONMessage exportExcelByGroupMember(HttpServletRequest request, HttpServletResponse response,
												@RequestParam(defaultValue = "") String roomId) {
		try {
			Workbook workBook = SKBeanUtils.getRoomManagerImplForIM().exprotExcelGroupMembers(roomId, request,
					response);
			ServletOutputStream out = response.getOutputStream();
			workBook.write(out);
			// ?????????????????????
			out.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return JSONMessage.success();
	}

	/**
	 * ????????????????????????
	 */
	@RequestMapping(value = "/getUserRegisterCount")
	public JSONMessage getUserRegisterCount(@RequestParam(defaultValue = "0") int pageIndex,
											@RequestParam(defaultValue = "100") int pageSize, @RequestParam(defaultValue = "2") short timeUnit,
											@RequestParam(defaultValue = "") String startDate, @RequestParam(defaultValue = "") String endDate) {

		try {

			Object data = getUserManager().getUserRegisterCount(startDate.trim(), endDate.trim(), timeUnit);
			return JSONMessage.success(null, data);

		} catch (MongoCommandException e) {
			return JSONMessage.success(0);
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getMessage());
		}

	}

	/**
	 * ??????????????????????????????????????????????????? ??????
	 */
	@RequestMapping(value = "/countNum")
	public JSONMessage countNum(HttpServletRequest request, HttpServletResponse response) {

		try {
			long userNum = getUserManager().count();
			long roomNum = getRoomManagerImplForIM().countRoomNum();
			long msgNum = SKBeanUtils.getTigaseManager().getMsgCountNum();
			long friendsNum = SKBeanUtils.getFriendsManager().count();

			Map<String, Long> dataMap = new HashMap<String, Long>();
			dataMap.put("userNum", userNum);
			dataMap.put("roomNum", roomNum);
			dataMap.put("msgNum", msgNum);
			dataMap.put("friendsNum", friendsNum);
			return JSONMessage.success(null, dataMap);

		} catch (MongoCommandException e) {
			return JSONMessage.success(0);
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getMessage());
		}

	}

	/**
	 * ????????????????????????
	 */
	@RequestMapping(value = "/chatMsgCount")
	public JSONMessage chatMsgCount(@RequestParam(defaultValue = "0") int pageIndex,
									@RequestParam(defaultValue = "100") int pageSize, @RequestParam(defaultValue = "2") short timeUnit,
									@RequestParam(defaultValue = "") String startDate, @RequestParam(defaultValue = "") String endDate) {

		try {

			Object data = SKBeanUtils.getTigaseManager().getChatMsgCount(startDate.trim(), endDate.trim(), timeUnit);
			return JSONMessage.success(null, data);

		} catch (MongoCommandException e) {
			return JSONMessage.success(0);
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getMessage());
		}

	}

	/**
	 * ???????????????????????????
	 */
	@RequestMapping(value = "/groupMsgCount")
	public JSONMessage groupMsgCount(@RequestParam String roomId, @RequestParam(defaultValue = "0") int pageIndex,
									 @RequestParam(defaultValue = "100") int pageSize, @RequestParam(defaultValue = "2") short timeUnit,
									 @RequestParam(defaultValue = "") String startDate, @RequestParam(defaultValue = "") String endDate) {

		try {

			Object data = SKBeanUtils.getTigaseManager().getGroupMsgCount(roomId, startDate.trim(), endDate.trim(),
					timeUnit);
			return JSONMessage.success(null, data);

		} catch (MongoCommandException e) {
			return JSONMessage.success(0);
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getMessage());
		}

	}

	/**
	 * ????????????????????????
	 */
	@RequestMapping(value = "/addFriendsCount")
	public JSONMessage addFriendsCount(@RequestParam(defaultValue = "0") int pageIndex,
									   @RequestParam(defaultValue = "100") int pageSize, @RequestParam(defaultValue = "2") short timeUnit,
									   @RequestParam(defaultValue = "") String startDate, @RequestParam(defaultValue = "") String endDate) {

		try {

			Object data = SKBeanUtils.getFriendsManager().getAddFriendsCount(startDate.trim(), endDate.trim(),
					timeUnit);
			return JSONMessage.success(null, data);

		} catch (MongoCommandException e) {
			return JSONMessage.success(0);
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getMessage());
		}

	}

	/**
	 * ????????????????????????
	 */
	@RequestMapping(value = "/addRoomsCount")
	public JSONMessage addRoomsCount(@RequestParam(defaultValue = "0") int pageIndex,
									 @RequestParam(defaultValue = "100") int pageSize, @RequestParam(defaultValue = "2") short timeUnit,
									 @RequestParam(defaultValue = "") String startDate, @RequestParam(defaultValue = "") String endDate) {

		try {

			Object data = SKBeanUtils.getRoomManager().addRoomsCount(startDate.trim(), endDate.trim(), timeUnit);
			return JSONMessage.success(null, data);

		} catch (MongoCommandException e) {
			return JSONMessage.success(0);
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getMessage());
		}

	}

	/**
	 * ????????????????????????
	 *
	 * @param pageIndex
	 * @param pageSize
	 * @param sign
	 * @param startDate
	 * @param endDate
	 * @param timeUnit
	 * @throws Exception
	 */
	@RequestMapping(value = "/getUserStatusCount")
	public JSONMessage getUserStatusCount(@RequestParam(defaultValue = "0") int pageIndex,
										  @RequestParam(defaultValue = "100") int pageSize, @RequestParam(defaultValue = "2") short timeUnit,
										  @RequestParam(defaultValue = "") String startDate, @RequestParam(defaultValue = "") String endDate)
			throws Exception {

		try {

			Object data = SKBeanUtils.getUserManager().userOnlineStatusCount(startDate.trim(), endDate.trim(),
					timeUnit);
			return JSONMessage.success(null, data);

		} catch (MongoCommandException e) {
			return JSONMessage.success(0);
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * @Description:???????????????????????????????????????
	 * @param type
	 *            (type = 0????????????????????????,type=1????????????????????????,type=2????????????????????????)
	 * @param pageSize
	 * @return
	 **/
	@SuppressWarnings("static-access")
	@RequestMapping(value = "/beReport")
	public JSONMessage beReport(@RequestParam(defaultValue = "0") int type,
								@RequestParam(defaultValue = "0") int sender, @RequestParam(defaultValue = "") String receiver,
								@RequestParam(defaultValue = "0") int pageIndex, @RequestParam(defaultValue = "25") int pageSize) {
		Map<String, Object> dataMap = Maps.newConcurrentMap();
		JSONMessage jsonMessage = new JSONMessage();
		try {
			dataMap = SKBeanUtils.getUserManager().getReport(type, sender, receiver, pageIndex, pageSize);
			logger.info("???????????????" + JSONObject.toJSONString(dataMap.get("data")));
			if (!dataMap.isEmpty()) {
				List<Report> reportList = (List<Report>) dataMap.get("data");
				long total = (long) dataMap.get("count");
				return jsonMessage.success(null, new PageVO(reportList, total, pageIndex, pageSize, total));
			}
		} catch (Exception e) {
			e.printStackTrace();
			return jsonMessage.failure(e.getMessage());
		}
		return jsonMessage;

	}
	@RequestMapping("/isLockWebUrl")
	public JSONMessage isLockWebUrl(@RequestParam(defaultValue = "") String webUrlId,@RequestParam(defaultValue = "-1")int webStatus){
		if (StringUtil.isEmpty(webUrlId))
			return JSONMessage.failure("webUrl is null");
		Query<Report> query = SKBeanUtils.getDatastore().createQuery(Report.class).field("_id").equal(new ObjectId(webUrlId));
		if(null == query.get())
			return JSONMessage.failure("??????????????????????????????");
		UpdateOperations<Report> ops = SKBeanUtils.getDatastore().createUpdateOperations(Report.class);
		ops.set("webStatus", webStatus);
		SKBeanUtils.getDatastore().update(query, ops);
		return JSONMessage.success();
	}

	/**
	 * ????????????
	 *
	 * @param response
	 * @param id
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/deleteReport")
	public JSONMessage deleteReport(HttpServletResponse response, @RequestParam String id) throws IOException {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return JSONMessage.failure("????????????");
		}

		BasicDBObject query = new BasicDBObject("_id", parse(id));
		getUserManager().getDatastore().getDB().getCollection("Report").remove(query);
		return JSONMessage.success();
	}

	/**
	 * api ????????????
	 *
	 * @param keyWorld
	 * @param page
	 * @param limit
	 * @return
	 */
	@RequestMapping(value = "/ApiLogList")
	public JSONMessage apiLogList(@RequestParam(defaultValue = "") String keyWorld,
								  @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "15") int limit) {

		try {
			PageResult<SysApiLog> data = SKBeanUtils.getAdminManager().apiLogList(keyWorld, page, limit);
			return JSONMessage.success(data);

		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}

	}

	/**
	 * ?????? api ??????
	 *
	 * @param apiLogId
	 * @param type
	 * @return
	 */
	@RequestMapping(value = "/delApiLog")
	public JSONMessage delApiLog(@RequestParam(defaultValue = "") String apiLogId,
								 @RequestParam(defaultValue = "0") int type) {

		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}

			SKBeanUtils.getAdminManager().deleteApiLog(apiLogId, type);
			return JSONMessage.success();
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * @Description:???????????????????????????
	 * @param limit
	 * @param page
	 * @return
	 **/
	@RequestMapping(value = "/getFriendsMsgList")
	public JSONMessage getFriendsMsgList(@RequestParam(defaultValue = "0") Integer page,
										 @RequestParam(defaultValue = "10") Integer limit, @RequestParam(defaultValue = "") String nickname,
										 @RequestParam(defaultValue = "0") Integer userId) {
		try {
			PageResult<Msg> data = SKBeanUtils.getMsgRepository().getMsgList(page, limit, nickname, userId);
			return JSONMessage.success(data);
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * @Description:?????????????????????
	 * @param userId
	 * @param messageId
	 * @return
	 **/
	@RequestMapping(value = "/deleteFriendsMsg")
	public JSONMessage deleteMsg(@RequestParam String messageId) {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return JSONMessage.failure("????????????");
		}

		JSONMessage jMessage;
		if (StringUtil.isEmpty(messageId)) {
			jMessage = Result.ParamsAuthFail;
		} else {
			try {
				String[] messageIds = StringUtil.getStringList(messageId);
				boolean ok = SKBeanUtils.getMsgRepository().delete(messageIds);
				jMessage = ok ? JSONMessage.success() : JSONMessage.failure(null);
			} catch (Exception e) {
				logger.error("???????????????????????????", e);
				jMessage = JSONMessage.error(e);
			}
		}
		return jMessage;
	}

	/**
	 * @Description:?????????????????????
	 * @param userId
	 * @param state
	 * @param roomId
	 * @return
	 **/
	@RequestMapping(value = "/lockingMsg")
	public JSONMessage lockingMsg(@RequestParam String msgId, @RequestParam int state) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}

			SKBeanUtils.getMsgRepository().lockingMsg(new ObjectId(msgId), state);
			return JSONMessage.success();
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * @Description:?????????????????????
	 * @param userId
	 * @param state
	 * @param roomId
	 * @return
	 **/
	@RequestMapping(value = "/commonListMsg")
	public JSONMessage commonListMsg(@RequestParam String msgId, @RequestParam(defaultValue = "0") Integer page,
									 @RequestParam(defaultValue = "10") Integer limit) {
		try {
			PageResult<Comment> result = SKBeanUtils.getMsgRepository().commonListMsg(new ObjectId(msgId), page, limit);
			return JSONMessage.success(result);
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * @Description:?????????????????????
	 * @param userId
	 * @param state
	 * @param roomId
	 * @return
	 **/
	@RequestMapping(value = "/praiseListMsg")
	public JSONMessage praiseListMsg(@RequestParam String msgId, @RequestParam(defaultValue = "0") Integer page,
									 @RequestParam(defaultValue = "10") Integer limit) {
		try {
			PageResult<Praise> result = SKBeanUtils.getMsgRepository().praiseListMsg(new ObjectId(msgId), page, limit);
			return JSONMessage.success(result);
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * @Description:??????????????????
	 * @param messageId
	 * @param commentId
	 * @return
	 **/
	@RequestMapping(value = "/comment/delete")
	public JSONMessage deleteComment(@RequestParam String messageId, @RequestParam String commentId) {
		JSONMessage jMessage;
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return JSONMessage.failure("????????????");
		}
		if (StringUtil.isEmpty(messageId) || StringUtil.isEmpty(commentId)) {
			jMessage = Result.ParamsAuthFail;
		} else {
			try {
				boolean ok = SKBeanUtils.getMsgCommentRepository().delete(new ObjectId(messageId), commentId);
				jMessage = ok ? JSONMessage.success() : JSONMessage.failure(null);
			} catch (Exception e) {
				logger.error("??????????????????", e);
				jMessage = JSONMessage.error(e);
			}
		}
		return jMessage;
	}

	/**
	 * @Description:??????????????????????????????
	 * @param userId
	 * @param status
	 * @return
	 **/
	@RequestMapping("/changeStatus")
	public JSONMessage changeStatus(@RequestParam int userId, @RequestParam int status) {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return JSONMessage.failure("????????????");
		}
		getUserManager().changeStatus(userId, status);
		return JSONMessage.success();
	}

	/**
	 * @Description:????????????????????????
	 * @param userId
	 * @param status
	 * @param type
	 * @return
	 **/
	@RequestMapping("/systemRecharge")
	public JSONMessage systemRecharge(@RequestParam(defaultValue = "0") int userId,
									  @RequestParam(defaultValue = "0") int type, @RequestParam(defaultValue = "0") int page,
									  @RequestParam(defaultValue = "10") int limit, @RequestParam(defaultValue = "") String startDate,
									  @RequestParam(defaultValue = "") String endDate, @RequestParam(defaultValue = "") String tradeNo) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.FINANCE){
				return JSONMessage.failure("????????????");
			}
			PageResult<ConsumeRecord> result = SKBeanUtils.getConsumeRecordManager().recharge(userId, type, page, limit,
					startDate, endDate ,tradeNo);
			return JSONMessage.success(result);
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getErrMessage());
		}
	}

	/**
	 * ????????????
	 *
	 * @param money
	 * @param type
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/Recharge")
	public JSONMessage Recharge(Double money, int userId) throws Exception {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN){
			return JSONMessage.failure("????????????");
		}
		/*if(1 != money)
			return JSONMessage.failure("????????????????????????");*/
		// ????????????????????????
		if (null == getUserManager().getUser(userId)) {
			return JSONMessage.failure("????????????, ???????????????!");
		}

		String tradeNo = StringUtil.getOutTradeNo();

//		Map<String, Object> data = Maps.newHashMap();
		// ??????????????????
		ConsumeRecord record = new ConsumeRecord();
		record.setUserId(userId);
		record.setTradeNo(tradeNo);
		record.setMoney(money);
		record.setStatus(KConstants.OrderStatus.END);
		record.setType(KConstants.ConsumeType.SYSTEM_RECHARGE);
		record.setPayType(KConstants.PayType.SYSTEMPAY); // type = 3 ?????????????????????
		record.setDesc("??????????????????");
		record.setTime(DateUtil.currentTimeSeconds());
		record.setOperationAmount(money);
		try {
			Double balance = getUserManager().rechargeUserMoeny(userId, money, KConstants.MOENY_ADD);
			record.setCurrentBalance(balance);
			SKBeanUtils.getConsumeRecordManager().save(record);
//			data.put("balance", balance);
			return JSONMessage.success(balance);
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}

	}

	/** @Description:????????????
	 * @param money
	 * @param userId
	 * @return
	 **/
	@RequestMapping("/handCash")
	public JSONMessage handCash(Double money, int userId){
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN){
			return JSONMessage.failure("????????????");
		}
		/*if(1 != money)
			return JSONMessage.failure("????????????????????????");*/
		// ????????????????????????
		if (null == getUserManager().getUser(userId)) {
			return JSONMessage.failure("????????????, ???????????????!");
		}else{
			Double balance = getUserManager().getUser(userId).getBalance();
			if(balance < money)
				return JSONMessage.failure("????????????");
		}
		String tradeNo = StringUtil.getOutTradeNo();
		// ??????????????????
		ConsumeRecord record = new ConsumeRecord();
		record.setUserId(userId);
		record.setTradeNo(tradeNo);
		record.setMoney(money);
		record.setStatus(KConstants.OrderStatus.END);
		record.setType(KConstants.ConsumeType.SYSTEM_HANDCASH);
		record.setPayType(KConstants.PayType.SYSTEMPAY); // type = 3 ?????????????????????
		record.setDesc("??????????????????");
		record.setTime(DateUtil.currentTimeSeconds());
		record.setOperationAmount(money);
		try {
			Double balance = getUserManager().rechargeUserMoeny(userId, money, KConstants.MOENY_REDUCE);
			record.setCurrentBalance(balance);
			SKBeanUtils.getConsumeRecordManager().save(record);
			return JSONMessage.success(balance);
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ????????????
	 *
	 * @param money
	 * @param type
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "/userBill")
	public JSONMessage userBill(@RequestParam int userId, int page, int limit, @RequestParam(defaultValue = "") String startDate,
								@RequestParam(defaultValue = "") String endDate,@RequestParam(defaultValue = "0") int type) throws Exception {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.FINANCE){
				return JSONMessage.failure("????????????");
			}
			// ????????????????????????
			if (null == getUserManager().getUser(userId)) {
				return JSONMessage.failure("???????????????!");
			}
			PageResult<DBObject> result = SKBeanUtils.getConsumeRecordManager().consumeRecordList(userId, page,
					limit, (byte) 1,startDate,endDate,type);
			return JSONMessage.success(result);

		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}

	}

	@RequestMapping(value = "/consumeRecordInfo")
	public JSONMessage consumeRecordInfo(String tradeNo){
		try {
			PageResult<ConsumeRecord> recordInfo = SKBeanUtils.getConsumeRecordManager().getConsumeRecordByTradeNo(tradeNo);
			return JSONMessage.success(recordInfo);
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ???????????????
	 *
	 * @param server
	 * @return
	 */
	@RequestMapping(value = "/addServerList")
	public JSONMessage addServerList(@ModelAttribute ServerListConfig server) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getAdminManager().addServerList(server);
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ?????????????????????
	 *
	 * @param id
	 * @param pageIndex
	 * @param limit
	 * @return
	 */
	@RequestMapping(value = "/serverList")
	public JSONMessage serverList(@RequestParam(defaultValue = "") String id,
								  @RequestParam(defaultValue = "0") int pageIndex, @RequestParam(defaultValue = "10") int limit) {
		PageResult<ServerListConfig> result = SKBeanUtils.getAdminManager()
				.getServerList((!StringUtil.isEmpty(id) ? new ObjectId(id) : null), pageIndex, limit);
		return JSONMessage.success(null, result);
	}

	@RequestMapping(value = "/findServerByArea")
	public JSONMessage findServerByArea(@RequestParam(defaultValue = "") String area) {
		PageResult<ServerListConfig> result = SKBeanUtils.getAdminManager().findServerByArea(area);
		return JSONMessage.success(null, result);
	}

	/**
	 * ???????????????
	 *
	 * @param server
	 * @return
	 */
	@RequestMapping(value = "/updateServer")
	public JSONMessage updateServer(@ModelAttribute ServerListConfig server) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getAdminManager().updateServer(server);
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ???????????????
	 *
	 * @param id
	 * @return
	 */
	@RequestMapping(value = "/deleteServer")
	public JSONMessage deleteServer(@RequestParam String id) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getAdminManager().deleteServer(new ObjectId(id));
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ??????????????????
	 *
	 * @param area
	 * @param pageIndex
	 * @param limit
	 * @return
	 */
	@RequestMapping(value = "/areaConfigList")
	public JSONMessage areaConfigList(@RequestParam(defaultValue = "") String area,
									  @RequestParam(defaultValue = "0") int pageIndex, @RequestParam(defaultValue = "10") int limit) {
		PageResult<AreaConfig> result = SKBeanUtils.getAdminManager().areaConfigList(area, pageIndex, limit);
		return JSONMessage.success(result);
	}

	/**
	 * ??????????????????
	 *
	 * @param area
	 * @return
	 */
	@RequestMapping(value = "/addAreaConfig")
	public JSONMessage addAreaConfig(@ModelAttribute AreaConfig area) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getAdminManager().addAreaConfig(area);
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}

	}

	/**
	 * ??????????????????
	 *
	 * @param area
	 * @return
	 */
	@RequestMapping(value = "/updateAreaConfig")
	public JSONMessage updateAreaConfig(@ModelAttribute AreaConfig area) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getAdminManager().updateAreaConfig(area);
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ??????????????????
	 *
	 * @param id
	 * @return
	 */
	@RequestMapping(value = "/deleteAreaConfig")
	public JSONMessage deleteAreaConfig(@RequestParam String id) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getAdminManager().deleteAreaConfig(new ObjectId(id));
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ??????????????????
	 *
	 * @param urlConfig
	 * @return
	 */
	@RequestMapping(value = "/addUrlConfig")
	public JSONMessage addUrlConfig(@ModelAttribute UrlConfig urlConfig) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getAdminManager().addUrlConfig(urlConfig);
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ??????????????????
	 *
	 * @param id
	 * @param type
	 * @return
	 */
	@RequestMapping(value = "/findUrlConfig")
	public JSONMessage findUrlConfig(@RequestParam(defaultValue = "") String id,
									 @RequestParam(defaultValue = "") String type) {
		PageResult<UrlConfig> result = SKBeanUtils.getAdminManager()
				.findUrlConfig((!StringUtil.isEmpty(id) ? new ObjectId(id) : null), type);
		return JSONMessage.success(null, result);

	}

	/**
	 * ????????????
	 *
	 * @param id
	 * @return
	 */
	@RequestMapping(value = "/deleteUrlConfig")
	public JSONMessage deleteUrlConfig(@RequestParam(defaultValue = "") String id) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getAdminManager().deleteUrlConfig(!StringUtil.isEmpty(id) ? new ObjectId(id) : null);
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ???????????????
	 *
	 * @param centerConfig
	 * @return
	 */
	@RequestMapping(value = "/addcenterConfig")
	public JSONMessage addCenterConfig(@ModelAttribute CenterConfig centerConfig) {
		try {
			SKBeanUtils.getAdminManager().addCenterConfig(centerConfig);
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ?????????????????????
	 *
	 * @param type
	 * @param id
	 * @return
	 */
	@RequestMapping(value = "/findCenterConfig")
	public JSONMessage findCentConfig(@RequestParam(defaultValue = "") String type,
									  @RequestParam(defaultValue = "") String id) {
		PageResult<CenterConfig> result = SKBeanUtils.getAdminManager().findCenterConfig(type,
				(!StringUtil.isEmpty(id) ? new ObjectId(id) : null));
		return JSONMessage.success(null, result);
	}

	/**
	 * ?????????????????????
	 *
	 * @param id
	 * @return
	 */
	@RequestMapping(value = "/deleteCenter")
	public JSONMessage deleteCenter(@RequestParam String id) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getAdminManager().deleteCenter(new ObjectId(id));
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.success();
		}

	}

	/**
	 * ???????????????
	 *
	 * @param totalConfig
	 * @return
	 */
	@RequestMapping(value = "/addTotalConfig")
	public JSONMessage addTotalConfig(@ModelAttribute TotalConfig totalConfig) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getAdminManager().addTotalConfig(totalConfig);
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}
	}

	@RequestMapping(value = "/addAdmin")
	public JSONMessage addAdmin(@RequestParam(defaultValue = "86") Integer areaCode, @RequestParam String telePhone,
								@RequestParam byte role, @RequestParam(defaultValue = "0") Integer type) {

		try {
			// ????????????
			byte userRole = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(userRole!=KConstants.Admin_Role.SUPER_ADMIN&&userRole!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			// ????????????????????????
			// User user = getUserManager().getUser(areaCode+account);
			/*
			 * Admin admin =
			 * SKBeanUtils.getAdminManager().findAdminByAccount(account);
			 * if(admin!=null) { return JSONMessage.failure("??????????????????"); }
			 * SKBeanUtils.getAdminManager().addAdmin(account, password, role);
			 */
			SKBeanUtils.getRoleManager().addAdmin(areaCode + telePhone, telePhone, role, type);
			return JSONMessage.success();
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}

	}

	@RequestMapping(value = "/adminList")
	public JSONMessage adminList(@RequestParam String adminId, @RequestParam(defaultValue = "") String keyWorld,
								 @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int limit,
								 @RequestParam(defaultValue = "0") int type, @RequestParam(defaultValue = "0") Integer userId) {
		try {

			Role userRole = SKBeanUtils.getRoleManager().getUserRole(getUserManager().getUser(adminId).getUserId(),
					null, 5);
			if(userRole.getRole()!=KConstants.Admin_Role.ADMIN&&userRole.getRole()!=KConstants.Admin_Role.SUPER_ADMIN){
				return JSONMessage.failure("????????????");
			}else{
				if(userRole.getRole()==type){
					return JSONMessage.failure("????????????");
				}
			}
			if (userRole != null && userRole.getRole() == KConstants.Admin_Role.ADMIN || userRole.getRole() == KConstants.Admin_Role.SUPER_ADMIN ||
					userRole.getRole() == KConstants.Admin_Role.TOURIST|| userRole.getRole() == KConstants.Admin_Role.CUSTOMER ||
					userRole.getRole() == KConstants.Admin_Role.FINANCE) {
				PageResult<Role> result = SKBeanUtils.getRoleManager().adminList(keyWorld, page, limit, type, userId);
				return JSONMessage.success(result);
			} else {
				return JSONMessage.failure("????????????");
			}
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * @Description:??????????????????????????????
	 * @param adminId
	 * @param toAdminId
	 * @param admin
	 * @return
	 **/
	@RequestMapping(value = "/modifyAdmin")
	public JSONMessage modifyAdmin(@RequestParam Integer adminId, @RequestParam(defaultValue = "") String password,
								   @ModelAttribute Role role) {

		try {
			// ????????????
			byte userRole = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(userRole!=KConstants.Admin_Role.SUPER_ADMIN&&userRole!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}

			if (!StringUtil.isEmpty(password)) {
				User user = SKBeanUtils.getUserRepository().getUser(adminId);
				if (!password.equals(user.getPassword()))
					return JSONMessage.failure("????????????");
			}
			Role oAdmin = SKBeanUtils.getRoleManager().getUserRole(adminId, null, 5);
			if (oAdmin != null && oAdmin.getRole() == 6 || oAdmin.getRole() == 5) { // role
				// =
				// 1
				// ???????????????
				Object result = SKBeanUtils.getRoleManager().modifyRole(role);
				return JSONMessage.success(result);
			} else {
				return JSONMessage.failure("????????????");
			}

		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * @Description:????????????????????????
	 * @param adminId
	 * @return
	 **/
	@RequestMapping(value = "/delAdmin")
	public JSONMessage deleteAdmin(@RequestParam String adminId, @RequestParam Integer type) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getRoleManager().delAdminById(adminId, type);
			return JSONMessage.success();
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}

	}

	/**
	 * @Description:???????????????????????????
	 * @param userId
	 * @param toUserId
	 * @return
	 **/
	@RequestMapping("/friendsChatRecord")
	public JSONMessage friendsChatRecord(@RequestParam(defaultValue = "0") Integer userId,
										 @RequestParam(defaultValue = "0") Integer toUserId, @RequestParam(defaultValue = "0") int page,
										 @RequestParam(defaultValue = "10") int limit) {
		try {
			PageResult<DBObject> result = SKBeanUtils.getFriendsManager().chardRecord(userId, toUserId, page, limit);
			return JSONMessage.success(result);
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * @Description:????????????????????????????????????
	 * @param messageId
	 * @return
	 **/
	@RequestMapping("/delFriendsChatRecord")
	public JSONMessage delFriendsChatRecord(@RequestParam(defaultValue = "") String messageId) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			if (StringUtil.isEmpty(messageId))
				return JSONMessage.failure("????????????");
			String[] strMessageIds = StringUtil.getStringList(messageId);
			SKBeanUtils.getFriendsManager().delFriendsChatRecord(strMessageIds);
			return JSONMessage.success();
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * @Description:?????????????????????
	 * @param toUserId
	 * @param type
	 *            0 ??? ?????????????????? 1??????????????????
	 * @return
	 **/
	@SuppressWarnings("static-access")
	@RequestMapping("/blacklist/operation")
	public JSONMessage blacklistOperation(@RequestParam Integer userId, @RequestParam Integer toUserId,
										  @RequestParam(defaultValue = "0") Integer type,@RequestParam(defaultValue = "")Integer adminUserId) {
		JSONMessage jsonMessage = null;
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			if (0 == type) {
				if (SKBeanUtils.getFriendsManager().isBlack(userId, toUserId))
					return jsonMessage.failure("????????????????????????");
				Friends data = SKBeanUtils.getFriendsManager().consoleAddBlacklist(userId, toUserId,adminUserId);
				return jsonMessage.success("?????????????????????", data);
			} else if (1 == type) {
				if (!SKBeanUtils.getFriendsManager().isBlack(userId, toUserId))
					return jsonMessage.failure("?????????" + toUserId + "????????????????????????");
				Friends data = SKBeanUtils.getFriendsManager().consoleDeleteBlacklist(userId, toUserId,adminUserId);
				return jsonMessage.success("??????????????????", data);
			}
		} catch (Exception e) {
			return jsonMessage.failure(e.getMessage());
		}
		return jsonMessage;

	}

	/**
	 * ????????????app??????
	 *
	 * @param pageIndex
	 * @param pageSize
	 * @return
	 */
	@RequestMapping(value = "/openAppList")
	public JSONMessage openAppList(@RequestParam(defaultValue = "-2") int status,@RequestParam(defaultValue="0") int type,
								   @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int limit,
								   @RequestParam(defaultValue = "") String keyWorld) {
		try {
			PageResult<SkOpenApp> list = SKBeanUtils.getAdminManager().openAppList(status, type, page, limit, keyWorld);
			return JSONMessage.success(list);
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}

	}

	/**
	 * ????????????app??????
	 *
	 * @param id
	 * @return
	 */
	@RequestMapping(value = "/openAppDetail")
	public JSONMessage openAppDetail(@RequestParam(defaultValue = "") String id) {
		try {
			Object data = SKBeanUtils.getOpenAppManage().appInfo(new ObjectId(id));
			return JSONMessage.success(data);
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ???????????????????????????????????????
	 *
	 * @param id
	 * @param userId
	 * @param status
	 * @return
	 */
	@RequestMapping(value = "/approvedAPP")
	public JSONMessage approved(@RequestParam(defaultValue = "") String id,
								@RequestParam(defaultValue = "") String userId, @RequestParam(defaultValue = "0") int status,
								@RequestParam(defaultValue = "") String reason) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getOpenAppManage().approvedAPP(id, status, userId, reason);
			return JSONMessage.success();
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ????????????????????????app??????
	 *
	 * @param skOpenApp
	 * @return
	 */
	@RequestMapping(value = "/checkPermission")
	public JSONMessage checkPermission(@ModelAttribute SkOpenApp skOpenApp) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getOpenAppManage().openAccess(skOpenApp);
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}
	}

//	/**
//	 * ????????????????????????web??????
//	 *
//	 * @param skOpenWeb
//	 * @return
//	 */
//	@RequestMapping(value = "/checkWebPermission")
//	public JSONMessage checkWebPermission(@ModelAttribute SkOpenWeb skOpenWeb) {
//		try {
//			SKBeanUtils.getOpenWebAppManage().openAccess(skOpenWeb);
//			return JSONMessage.success();
//		} catch (Exception e) {
//			e.printStackTrace();
//			return JSONMessage.failure(e.getMessage());
//		}
//	}

	/**
	 * ????????????????????????
	 *
	 * @param id
	 * @param accountId
	 * @return
	 */
	@RequestMapping(value = "/deleteOpenApp")
	public JSONMessage deleteOpenApp(@RequestParam(defaultValue = "") String id,
									 @RequestParam(defaultValue = "") String accountId) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getOpenAppManage().deleteAppById(new ObjectId(id), accountId);
			return JSONMessage.success();
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ??????????????????????????????
	 *
	 * @param pageIndex
	 * @param pageSize
	 * @return
	 */
	@RequestMapping(value = "/checkLogList")
	public JSONMessage checkLogList(@RequestParam(defaultValue = "0") int page,
									@RequestParam(defaultValue = "10") int limit) {
		try {
			PageResult<SkOpenCheckLog> data = SKBeanUtils.getOpenCheckLogManage().getOpenCheckLogList(page, limit);
			return JSONMessage.success(data);
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ????????????????????????
	 *
	 * @param id
	 * @return
	 */
	@RequestMapping(value = "/delOpenCheckLog")
	public JSONMessage delOpenCheckLog(@RequestParam(defaultValue = "") String id) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getOpenCheckLogManage().delOpenCheckLog(new ObjectId(id));
			return JSONMessage.success();
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ???????????????????????????
	 *
	 * @param pageIndex
	 * @param pageSize
	 * @return
	 */
	@RequestMapping(value = "/developerList")
	public JSONMessage developerList(@RequestParam(defaultValue = "0") int page,
									 @RequestParam(defaultValue = "10") int limit, @RequestParam(defaultValue = "-2") int status,
									 @RequestParam(defaultValue = "") String keyWorld) {
		try {
			PageResult<SkOpenAccount> data = SKBeanUtils.getOpenAccountManage().developerList(page, limit, status,
					keyWorld);
			return JSONMessage.success(data);
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ???????????????????????????
	 *
	 * @param userId
	 * @return
	 */
	@RequestMapping(value = "/developerDetail")
	public JSONMessage developerDetail(@RequestParam(defaultValue = "") Integer userId) {
		try {
			Object data = SKBeanUtils.getOpenAccountManage().getOpenAccount(userId);
			return JSONMessage.success(data);
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ???????????????????????????
	 *
	 * @param id
	 * @return
	 */
	@RequestMapping(value = "/deleteDeveloper")
	public JSONMessage deleteDeveloper(@RequestParam(defaultValue = "") String id) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getOpenAccountManage().deleteDeveloper(new ObjectId(id));
			return JSONMessage.success();
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ????????????????????????????????????
	 *
	 * @param id
	 * @param userId
	 * @param status
	 * @return
	 */
	@RequestMapping(value = "/checkDeveloper")
	public JSONMessage checkDeveloper(@RequestParam(defaultValue = "") String id,
									  @RequestParam(defaultValue = "") String userId, @RequestParam(defaultValue = "0") int status) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getOpenAccountManage().checkDeveloper(new ObjectId(id), status);
			return JSONMessage.success();
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	// ??????????????????????????????????????????
	@RequestMapping(value = "/authInterface")
	public JSONMessage authInterface(@RequestParam(defaultValue="") String appId,@RequestParam(defaultValue="1") int type){
		try {
			SKBeanUtils.getOpenAppManage().authInterfaceWeb(appId, type);
			return JSONMessage.success();

		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	//???????????????
	@RequestMapping(value="/create/inviteCode")
	public JSONMessage createInviteCode(@RequestParam(defaultValue="20") int nums,@RequestParam int userId,@RequestParam short type) throws IOException{
		try {
			SKBeanUtils.getAdminManager().createInviteCode(nums, userId);
			return JSONMessage.success();
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}

	}

	// ???????????????
	@RequestMapping(value = "/inviteCodeList")
	public JSONMessage inviteCodeList(@RequestParam(defaultValue = "0") int userId,
									  @RequestParam(defaultValue = "") String keyworld, @RequestParam(defaultValue = "-1") short state,
									  @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int limit) {
		try {
			PageResult<InviteCode> data = SKBeanUtils.getAdminManager().inviteCodeList(userId, keyworld, state, page,
					limit);
			return JSONMessage.success(data);
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	// ???????????????
	@RequestMapping(value = "/delInviteCode")
	public JSONMessage delInviteCode(@RequestParam(defaultValue = "") int userId,
									 @RequestParam(defaultValue = "") String inviteCodeId) {

		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			boolean data = SKBeanUtils.getAdminManager().delInviteCode(userId, inviteCodeId);
			return JSONMessage.success(data);
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * @Description:??????????????????
	 * @param checkNum
	 *            ?????????????????????
	 * @param roomJid
	 *            ??????jid
	 * @param sendMsgNum
	 *            ???????????????
	 * @param timeInterval
	 *            ????????????????????????
	 * @return
	 **/
	@RequestMapping("/pressureTest")
	public JSONMessage pressureTest(@ModelAttribute PressureParam param, HttpServletRequest request) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			if(param.getTimeInterval() < 10)
				param.setTimeInterval(10);
			System.out.println("pressureTest ====> " + request.getSession().getCreationTime() + " "
					+ request.getSession().getId());
			List<String> jids = StringUtil.getListBySplit(param.getRoomJid(), ",");
			param.setJids(jids);
			param.setSendAllCount(param.getSendMsgNum() * jids.size());
			PressureParam.PressureResult result = SKBeanUtils.getPressureTestManager().mucTest(param);
			if (null == result) {
				return JSONMessage.failure("???????????? ?????? ?????????  ????????? ?????? ??????????????????");
			}
			return JSONMessage.success(result);
		} catch (ServiceException e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ?????????????????????
	 *
	 * @param page
	 * @param limit
	 * @param keyword
	 * @return
	 */
	@RequestMapping(value = "/musicList")
	public JSONMessage queryMusicList(@RequestParam(defaultValue = "0") int page,
									  @RequestParam(defaultValue = "10") Integer limit, @RequestParam(defaultValue = "") String keyword) {
		PageResult<MusicInfo> result = SKBeanUtils.getLocalSpringBeanManager().getAdminManager().queryMusicInfo(page,
				limit, keyword);
		return JSONMessage.success(result);
	}

	/**
	 * ?????????????????????
	 *
	 * @param musicInfo
	 * @return
	 */
	@RequestMapping(value = "/addMusic")
	public JSONMessage addMusic(@ModelAttribute MusicInfo musicInfo) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getLocalSpringBeanManager().getMusicManager().addMusicInfo(musicInfo);
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}

	}

	/**
	 * ?????????????????????
	 *
	 * @param musicInfoId
	 * @return
	 */
	@RequestMapping(value = "/deleteMusic")
	public JSONMessage deleteMusic(@RequestParam(defaultValue = "") String musicInfoId) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getLocalSpringBeanManager().getMusicManager().deleteMusicInfo(new ObjectId(musicInfoId));
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}

	}

	/**
	 * ?????????????????????
	 *
	 * @param musicInfo
	 * @return
	 */
	@RequestMapping(value = "/updateMusic")
	public JSONMessage updateMusic(@ModelAttribute MusicInfo musicInfo) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getLocalSpringBeanManager().getMusicManager().updateMusicInfo(musicInfo);
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ????????????
	 *
	 * @param userId
	 * @param page
	 * @param limit
	 * @return
	 */
	@RequestMapping(value = "/transferList")
	public JSONMessage transferList(@RequestParam(defaultValue = "") String userId,
									@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "15") int limit,
									@RequestParam(defaultValue = "") String startDate, @RequestParam(defaultValue = "") String endDate) {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.FINANCE){
			return JSONMessage.failure("????????????");
		}
		PageResult<Transfer> result = SKBeanUtils.getAdminManager().queryTransfer(page, limit, userId, startDate,
				endDate);
		return JSONMessage.success(result);
	}

	/**
	 * ????????????
	 *
	 * @param userId
	 * @param page
	 * @param limit
	 * @return
	 */
	@RequestMapping(value = "/paymentCodeList")
	public JSONMessage paymentCodeList(@RequestParam(defaultValue = "0") int userId,
									   @RequestParam(defaultValue = "0") int type, @RequestParam(defaultValue = "0") int page,
									   @RequestParam(defaultValue = "15") int limit, @RequestParam(defaultValue = "") String startDate,
									   @RequestParam(defaultValue = "") String endDate) {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.FINANCE){
			return JSONMessage.failure("????????????");
		}
		PageResult<ConsumeRecord> result = SKBeanUtils.getConsumeRecordManager().payment(userId, type, page, limit,
				startDate, endDate);
		return JSONMessage.success(result);
	}

	/**
	 * ???????????????????????????
	 *
	 * @param userId
	 * @param page
	 * @param limit
	 * @return
	 */
	@RequestMapping(value = "/getSdkLoginInfoList")
	public JSONMessage getSdkLoginInfoList(@RequestParam(defaultValue = "") String userId,
										   @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "15") int limit) {
		try {
			PageResult<SdkLoginInfo> result = SKBeanUtils.getAdminManager().getSdkLoginInfoList(page, limit, userId);
			return JSONMessage.success(result);
		} catch (NumberFormatException e) {
			logger.info("error : {}"+e.getMessage());
			return JSONMessage.success();
		} catch (Exception e) {
			return JSONMessage.failure(e.getMessage());
		}
	}

	/**
	 * ?????????????????????
	 *
	 * @param id
	 * @return
	 */
	@RequestMapping(value = "/deleteSdkLoginInfo")
	public JSONMessage deleteSdkLoginInfo(@RequestParam(defaultValue = "") String id) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getAdminManager().deleteSdkLoginInfo(new ObjectId(id));
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}
	}

	/** @Description:????????????
	 * @param appId
	 * @param callbackUrl
	 * @param response
	 **/
	@RequestMapping(value = "/oauth/authorize")
	public void authorizeUrl(String appId,String callbackUrl,HttpServletResponse response) {
		try {
			Map<String, String> webInfo = SKBeanUtils.getOpenAppManage().authorizeUrl(appId, callbackUrl);
			String webAppName = webInfo.get("webAppName");
			response.sendRedirect("/pages/websiteAuthorh/index.html"+"?"+"appId="+appId+"&"+"callbackUrl="+callbackUrl+"&webAppName="+URLEncoder.encode(webAppName,"UTF-8")+"&webAppsmallImg="+webInfo.get("webAppsmallImg"));
		} catch (Exception e) {
			logger.error(e.getMessage());
		}

	}

	/**
	 * ??????????????????
	 * @param type
	 * @param body
	 * @return
	 */
	@RequestMapping(value = "/sendSysNotice")
	public JSONMessage sendSysNotice(@RequestParam(defaultValue="0") Integer type,@RequestParam(defaultValue="") String body,@RequestParam(defaultValue="") String title,@RequestParam(defaultValue="") String url){
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
				return JSONMessage.failure("????????????");
			}
			if(StringUtil.isEmpty(body) || StringUtil.isEmpty(title))
				return JSONMessage.failure("???????????????????????????");
			SKBeanUtils.getAdminManager().sendSysNotice(type, body, title,url);
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}
	}

	@RequestMapping("/recharge/audit/bankCardInfo")
	public JSONMessage auditBankCardInfo(@RequestParam String bankCardId) {
		return JSONMessage.success(SKBeanUtils.getBankCardManager().getBankCardForce(new ObjectId(bankCardId)));
	}

	/**
	 * ????????????
	 * @param rechargeId
	 * @param auditStatus
	 * @return
	 */
	@RequestMapping("/recharge/audit")
	public JSONMessage rechargeAudit(@RequestParam String rechargeId,
									 @RequestParam byte auditStatus, String desc) {
		try {
			SKBeanUtils.getConsumeRecordManager().updateAuditStatus(ReqUtil.parseId(rechargeId), auditStatus, KConstants.FundsChangeType.DRAWINGS, desc);
		} catch (BizException e) {
			return JSONMessage.failure(e.getMessage());
		}
		return JSONMessage.success("????????????");
	}

	@RequestMapping("/trade/monitor")
	public JSONMessage tradeMonitor() {
		return JSONMessage.success(SKBeanUtils.getConsumeRecordManager().newTradeMonitor());
	}

	/**
	 * ?????????????????????????????????
	 *
	 * @param page
	 * @param limit
	 * @return
	 */
	@RequestMapping(value = "/friendsterWebsiteList")
	public JSONMessage queryReceiptQRList(@RequestParam(defaultValue = "0") int page,
										  @RequestParam(defaultValue = "10") Integer limit) {
		PageResult<FriendsterWebsite> result = SKBeanUtils.getLocalSpringBeanManager().getFriendsterWebsiteManager().queryFriendsterWebsiteList(page,
				limit);
		return JSONMessage.success(result);
	}

	@RequestMapping(value = "/getUrl")
	public JSONMessage queryReceiptQRList(@RequestParam String language) {
		String str=null;
		if(language.equals("hk")){
			str=String.format("http://%s%s",SKBeanUtils.getLocalSpringBeanManager().getApplicationConfig().getReceiptQR().getUrl(),"/pages/language/findhk.html");
		}else if(language.equals("en")){
			str=String.format("http://%s%s",SKBeanUtils.getLocalSpringBeanManager().getApplicationConfig().getReceiptQR().getUrl(),"/pages/language/finden.html");
		}else if(language.equals("zh")){
			str=String.format("http://%s%s",SKBeanUtils.getLocalSpringBeanManager().getApplicationConfig().getReceiptQR().getUrl(),"/pages/find/find.html");
		}
		return JSONMessage.success(str);
	}


//	@RequestMapping(value = "/getUrl")
//	public JSONMessage queryReceiptQRList() {
//		String str=String.format("http://%s%s",SKBeanUtils.getLocalSpringBeanManager().getApplicationConfig().getReceiptQR().getUrl(),"/pages/find/find.html");
//		return JSONMessage.success(str);
//	}



	/**
	 * ?????????????????????????????????
	 *
	 * @param page
	 * @param limit
	 * @return
	 */
	@RequestMapping(value = "/queryAllfriendsterWebsite")
	public JSONMessage queryAllfriendsterWebsite() {
		List<FriendsterWebsite> result = SKBeanUtils.getLocalSpringBeanManager().getFriendsterWebsiteManager().getFriendsterWebsiteList();
		return JSONMessage.success(result);
	}

	/**
	 * ?????????????????????????????????
	 *
	 * @return
	 */
	@RequestMapping(value = "/addFriendsterWebsite")
	public JSONMessage addReceiptQR(@ModelAttribute FriendsterWebsite friendsterWebsite) {
		try {
			if (null == friendsterWebsite || StringUtils.isAnyBlank(friendsterWebsite.getIcon(), friendsterWebsite.getTitle(), friendsterWebsite.getUrl())) {
				return JSONMessage.failure("????????????????????????");
			}
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if (role != KConstants.Admin_Role.SUPER_ADMIN && role != KConstants.Admin_Role.ADMIN) {
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getLocalSpringBeanManager().getFriendsterWebsiteManager().saveFriendsterWebsite(friendsterWebsite);
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}

	}

	/**
	 * ?????????????????????????????????
	 *
	 * @return
	 */
	@RequestMapping(value = "/deleteFriendsterWebsite")
	public JSONMessage deleteReceiptQR(@RequestParam(defaultValue = "") String id) {
		try {
			// ????????????
			byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
			if (role != KConstants.Admin_Role.SUPER_ADMIN && role != KConstants.Admin_Role.ADMIN) {
				return JSONMessage.failure("????????????");
			}
			SKBeanUtils.getLocalSpringBeanManager().getFriendsterWebsiteManager().deleteById(new ObjectId(id));
			return JSONMessage.success();
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure(e.getMessage());
		}

	}


	/**
	 * ??????ip?????????
	 *
	 * @param response
	 * @param id
	 * @param ip
	 * @throws IOException
	 */
	@RequestMapping(value = "/addBackIpWhite", method = {RequestMethod.POST})
	public void addBackIpWhite(HttpServletResponse response, @RequestParam(defaultValue = "") String id,
						   @RequestParam String ip) throws IOException {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if (role != KConstants.Admin_Role.SUPER_ADMIN) {
			return;
		}
		BackIpWhite ipWhite = null;
		Query<BackIpWhite> query = dsForRW.createQuery(BackIpWhite.class).field("ip").equal(ip);
		if(null==query.get()){
			ipWhite = new BackIpWhite();
			ipWhite.setIp(ip);
			ipWhite.setCreateTime(DateUtil.currentTimeSeconds());
			dsForRW.save(ipWhite);
		}else{
			UpdateOperations<BackIpWhite> ops = dsForRW.createUpdateOperations(BackIpWhite.class);
			if(!StringUtil.isEmpty(ip))
				ops.set("ip", ip);

			ops.set("createTime", DateUtil.currentTimeSeconds());
			dsForRW.findAndModify(query,ops);
		}
		response.sendRedirect("/console/backIpWhiteList");
	}


	/**
	 * ip???????????????
	 *
	 * @param ip
	 * @param pageIndex
	 * @param pageSize
	 * @return
	 */
	@RequestMapping(value = "/backIpWhiteList")
	public JSONMessage ipBlackList(@RequestParam(defaultValue = "") String ip,
								   @RequestParam(defaultValue = "0") int pageIndex, @RequestParam(defaultValue = "10") int pageSize) {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if (role != KConstants.Admin_Role.SUPER_ADMIN) {
			return JSONMessage.failure("????????????");
		}
		Query<BackIpWhite> query = dsForRW.createQuery(BackIpWhite.class);
		if (!StringUtil.isEmpty(ip)) {
			query.filter("ip", ip);
		}
		List<BackIpWhite> list = null;
		long total = 0;
		list = query.order("-createTime").asList(new FindOptions().skip(pageIndex * pageSize).limit(pageSize));
		total = query.count();
		return JSONMessage.success(null, new PageVO(list, total, pageIndex, pageSize, total));
	}


	/**
	 * ??????ip?????????
	 *
	 * @param response
	 * @param id
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/deleteBackIpWhite", method = {RequestMethod.POST})
	public JSONMessage deleteBackIpWhite(HttpServletResponse response, @RequestParam String id) throws IOException {
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if (role != KConstants.Admin_Role.SUPER_ADMIN) {
			return JSONMessage.failure("????????????");
		}
		Query<BackIpWhite> query = dsForRW.createQuery(BackIpWhite.class);
		query.field("_id").equal(new ObjectId(id));
		dsForRW.delete(query.get());
		return JSONMessage.success();
	}



	@RequestMapping("/bachAddUser")
	@ResponseBody
	public JSONMessage uploadPartMember(@RequestParam("file") MultipartFile file){
		// ????????????
		byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
		if(role!=KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.ADMIN){
			return JSONMessage.failure("????????????");
		}
		try {
			if (file!=null){
				JSONMessage result = this.uploadPartMembers(file);
				return result;
			}else {
				return JSONMessage.failure("????????????");
			}
		}catch (Exception e){
			e.printStackTrace();
			return JSONMessage.failure("????????????????????????????????????");
		}
	}

	public static JSONMessage uploadPartMembers(MultipartFile file) throws IOException {
		// ??????IO???
		InputStream in = null;
		try {
			in = file.getInputStream();
		} catch (IOException e) {
			e.printStackTrace();
			return JSONMessage.failure("???????????????????????????");
		}
		List<List<Object>> listob = null;
		try {
			listob = new ExcelUtils().getBankListByExcel(in, file.getOriginalFilename());
		} catch (Exception e) {
			e.printStackTrace();
			return JSONMessage.failure("?????????????????????????????????");
		}
		List<User> userList=new ArrayList<>();
		for (int i = 0; i < listob.size(); i++) {
			List<Object> lo = listob.get(i);
			User user =null;
			// ????????????????????????????????????????????????????????????
			try {
				if (getUserManager().isRegister(86+String.valueOf(lo.get(1)))){
					System.out.println("??????"+lo.get(1)+"?????????????????????");
					continue;
				}
				user = new User();
				user.setNickname(String.valueOf(lo.get(0)));
				user.setPhone(String.valueOf(lo.get(1)).trim());
				user.setPassword(String.valueOf(lo.get(2)).trim());
				Integer sex=String.valueOf(lo.get(3)).equals("???")?1:0;
				user.setSex(sex);
				userList.add(user);
			} catch (Exception e) {
				return JSONMessage.failure("?????????????????????????????????????????????");
			}
		}
		if(null!=userList&&!userList.isEmpty()&&userList.size()>0){
			Integer reqUserId = ReqUtil.getUserId();
			getUserManager().autoBatchAddUsers(userList,reqUserId);
			return JSONMessage.success("??????????????????!");
		}
		return JSONMessage.success("??????????????????!");

	}

}