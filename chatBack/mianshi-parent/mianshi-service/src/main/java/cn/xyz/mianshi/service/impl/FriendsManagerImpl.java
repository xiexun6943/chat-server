package cn.xyz.mianshi.service.impl;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.mongodb.*;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import cn.xyz.commons.constants.KConstants;
import cn.xyz.commons.ex.ServiceException;
import cn.xyz.commons.support.Callback;
import cn.xyz.commons.support.mongo.MongoOperator;
import cn.xyz.commons.utils.DateUtil;
import cn.xyz.commons.utils.ExcelUtil;
import cn.xyz.commons.utils.MapUtil;
import cn.xyz.commons.utils.ReqUtil;
import cn.xyz.commons.utils.StringUtil;
import cn.xyz.commons.utils.ThreadUtil;
import cn.xyz.commons.vo.JSONMessage;
import cn.xyz.mianshi.model.PageResult;
import cn.xyz.mianshi.model.PageVO;
import cn.xyz.mianshi.service.FriendsManager;
import cn.xyz.mianshi.utils.SKBeanUtils;
import cn.xyz.mianshi.vo.Friends;
import cn.xyz.mianshi.vo.NewFriends;
import cn.xyz.mianshi.vo.OfflineOperation;
import cn.xyz.mianshi.vo.User;
import cn.xyz.repository.mongo.FriendsRepositoryImpl;
import cn.xyz.service.KXMPPServiceImpl;
import cn.xyz.service.KXMPPServiceImpl.MessageBean;
import cn.xyz.service.RedisServiceImpl;

@Service
public class FriendsManagerImpl extends MongoRepository<Friends, ObjectId> implements FriendsManager {

    private static final String groupCode = "110";

    private static Logger Log = LoggerFactory.getLogger(FriendsManager.class);

    private static RedisServiceImpl getRedisServiceImpl() {
        return SKBeanUtils.getRedisService();
    }

    @Override
    public Datastore getDatastore() {
        return SKBeanUtils.getDatastore();
    }

    @Override
    public Class<Friends> getEntityClass() {
        return Friends.class;
    }

    private static FriendsRepositoryImpl getFriendsRepository() {
        FriendsRepositoryImpl getFriendsRepository = SKBeanUtils.getFriendsRepository();
        return getFriendsRepository;
    }

    ;

    private static UserManagerImpl getUserManager() {
        UserManagerImpl userManager = SKBeanUtils.getUserManager();
        return userManager;
    }

    ;

    @Override
    public Friends addBlacklist(Integer userId, Integer toUserId) {
        // ????????????AB??????
        Friends friendsAB = getFriendsRepository().getFriends(userId, toUserId);
        Friends friendsBA = getFriendsRepository().getFriends(toUserId, userId);

        if (null == friendsAB) {
            Friends friends = new Friends(userId, toUserId, getUserManager().getNickName(toUserId), Friends.Status.Stranger, Friends.Blacklist.Yes, 0);
            getFriendsRepository().saveFriends(friends);
        } else {
            // ????????????
            getFriendsRepository().updateFriends(new Friends(userId, toUserId, null, -1, Friends.Blacklist.Yes, (0 == friendsAB.getIsBeenBlack()) ? 0 : friendsAB.getIsBeenBlack()));
            getFriendsRepository().updateFriends(new Friends(toUserId, userId, null, null, (0 == friendsBA.getBlacklist() ? Friends.Blacklist.No : friendsBA.getBlacklist()), 1));
        }
        SKBeanUtils.getTigaseManager().deleteLastMsg(userId.toString(), toUserId.toString());
        //SKBeanUtils.getTigaseManager().deleteLastMsg(toUserId.toString(),userId.toString());
        // ??????????????????
        deleteFriendsInfo(userId, toUserId);
        // ??????????????????????????????
        updateOfflineOperation(userId, toUserId);
        return getFriendsRepository().getFriends(userId, toUserId);
    }

    /**
     * @param userId
     * @param toUserId
     * @Description: ?????????????????????????????????
     **/
    private void deleteAddressFriendsInfo(Integer userId, Integer toUserId) {
        // ???????????????id
        getRedisServiceImpl().delAddressBookFriendsUserIds(userId);
        getRedisServiceImpl().delAddressBookFriendsUserIds(toUserId);
        deleteFriendsInfo(userId, toUserId);
    }

    /**
     * @param userId
     * @param toUserId
     * @Description: ????????????????????????
     **/
    private void deleteFriendsInfo(Integer userId, Integer toUserId) {
        // ??????userIdsList
        getRedisServiceImpl().deleteFriendsUserIdsList(userId);
        getRedisServiceImpl().deleteFriendsUserIdsList(toUserId);
        // ????????????
        getRedisServiceImpl().deleteFriends(userId);
        getRedisServiceImpl().deleteFriends(toUserId);
    }

    // ???????????????????????????????????????????????????
    public Friends consoleAddBlacklist(Integer userId, Integer toUserId, Integer adminUserId) {
        // ????????????AB??????
        Friends friendsAB = getFriendsRepository().getFriends(userId, toUserId);
        Friends friendsBA = getFriendsRepository().getFriends(toUserId, userId);
        if (null == friendsAB) {
            Friends friends = new Friends(userId, toUserId, getUserManager().getNickName(toUserId), Friends.Status.Stranger, Friends.Blacklist.Yes, 0);
            getFriendsRepository().saveFriends(friends);
        } else {
            // ????????????
            getFriendsRepository().updateFriends(new Friends(userId, toUserId, null, -1, Friends.Blacklist.Yes, (0 == friendsAB.getIsBeenBlack()) ? 0 : friendsAB.getIsBeenBlack()));
            getFriendsRepository().updateFriends(new Friends(toUserId, userId, null, null, (0 == friendsBA.getBlacklist() ? Friends.Blacklist.No : friendsBA.getBlacklist()), 1));
        }
        SKBeanUtils.getTigaseManager().deleteLastMsg(userId.toString(), toUserId.toString());
        ThreadUtil.executeInThread(new Callback() {

            @Override
            public void execute(Object obj) {

                //xmpp????????????
                MessageBean messageBean = new MessageBean();
                messageBean.setType(KXMPPServiceImpl.joinBlacklist);
                messageBean.setFromUserId(adminUserId + "");
                messageBean.setFromUserName("?????????????????????");
                MessageBean beanVo = new MessageBean();
                beanVo.setFromUserId(userId + "");
                beanVo.setFromUserName(getUserManager().getNickName(userId));
                beanVo.setToUserId(toUserId + "");
                beanVo.setToUserName(getUserManager().getNickName(toUserId));
                messageBean.setObjectId(JSONObject.toJSONString(beanVo));
                messageBean.setMessageId(StringUtil.randomUUID());
                try {
                    List<Integer> userIdlist = new ArrayList<Integer>();
                    userIdlist.add(userId);
                    userIdlist.add(toUserId);
                    KXMPPServiceImpl.getInstance().send(messageBean, userIdlist);
                } catch (Exception e) {
                }

            }
        });
        // ??????????????????
        deleteFriendsInfo(userId, toUserId);
        // ??????????????????????????????
        updateOfflineOperation(userId, toUserId);
        return getFriendsRepository().getFriends(userId, toUserId);
    }


    public Friends updateFriends(Friends friends) {
        return getFriendsRepository().updateFriends(friends);
    }

    public boolean isBlack(Integer toUserId) {
        Friends friends = getFriends(ReqUtil.getUserId(), toUserId);
        if (friends == null)
            return false;
        return friends.getStatus() == -1 ? true : false;
    }

    public boolean isBlack(Integer userId, Integer toUserId) {
        Friends friends = getFriends(userId, toUserId);
        if (friends == null)
            return false;
        return friends.getStatus() == -1 ? true : false;
    }

    private void saveFansCount(int userId) {
        BasicDBObject q = new BasicDBObject("_id", userId);
        DBCollection dbCollection = getTigaseDatastore().getDB().getCollection("shiku_msgs_count");
        if (0 == dbCollection.count(q)) {
            BasicDBObject jo = new BasicDBObject("_id", userId);
            jo.put("count", 0);// ?????????
            jo.put("fansCount", 1);// ?????????
            dbCollection.insert(jo);
        } else {
            dbCollection.update(q, new BasicDBObject("$inc", new BasicDBObject("fansCount", 1)));
        }
    }

    @Override
    public boolean addFriends(Integer userId, Integer toUserId) {

        int toUserType = 0;
        List<Integer> toUserRoles = SKBeanUtils.getRoleManager().getUserRoles(toUserId);
        if (toUserRoles.size() > 0 && null != toUserRoles) {
            if (toUserRoles.contains(2))
                toUserType = 2;
        }
        int userType = 0;
        List<Integer> userRoles = SKBeanUtils.getRoleManager().getUserRoles(userId);
        if (userRoles.size() > 0 && null != userRoles) {
            if (userRoles.contains(2))
                userType = 2;
        }
        Friends friends = getFriends(userId, toUserId);
        if (null == friends) {
            getFriendsRepository().saveFriends(new Friends(userId, toUserId, getUserManager().getNickName(toUserId),
                    Friends.Status.Friends, 0, 0, toUserRoles, toUserType, 4));
            saveFansCount(toUserId);
        } else {
            saveFansCount(toUserId);
            Query<Friends> q = getDatastore().createQuery(Friends.class).field("userId").equal(userId).field("toUserId")
                    .equal(toUserId);
            UpdateOperations<Friends> ops = getDatastore().createUpdateOperations(Friends.class);
            ops.set("modifyTime", DateUtil.currentTimeSeconds());
            ops.set("status", Friends.Status.Friends);
            ops.set("toUserType", toUserType);
            ops.set("toFriendsRole", toUserRoles);
            getDatastore().findAndModify(q, ops);
        }
        Friends toFriends = getFriends(toUserId, userId);
        if (null == toFriends) {
            getFriendsRepository().saveFriends(new Friends(toUserId, userId, getUserManager().getNickName(userId),
                    Friends.Status.Friends, 0, 0, userRoles, userType, 4));
            saveFansCount(toUserId);
        } else {
            saveFansCount(toUserId);
            Query<Friends> q = getDatastore().createQuery(Friends.class).field("userId").equal(toUserId)
                    .field("toUserId").equal(userId);
            UpdateOperations<Friends> ops = getDatastore().createUpdateOperations(Friends.class);
            ops.set("modifyTime", DateUtil.currentTimeSeconds());
            ops.set("status", Friends.Status.Friends);
            ops.set("toUserType", userType);
            ops.set("toFriendsRole", userRoles);
            getDatastore().findAndModify(q, ops);
        }
        // ??????????????????????????????
        updateOfflineOperation(userId, toUserId);
        return true;
    }

    @Override
    public Friends deleteBlacklist(Integer userId, Integer toUserId) {
        // ????????????AB??????
        Friends friendsAB = getFriendsRepository().getFriends(userId, toUserId);
        Friends friendsBA = getFriendsRepository().getFriends(toUserId, userId);

        if (null == friendsAB) {
            // ?????????
        } else {
            // ??????????????????
            if (Friends.Blacklist.Yes == friendsAB.getBlacklist() && Friends.Status.Stranger == friendsAB.getStatus()) {
                // ????????????
                getFriendsRepository().deleteFriends(userId, toUserId);
            } else {
                // ????????????
                getFriendsRepository().updateFriends(new Friends(userId, toUserId, null, 2, Friends.Blacklist.No, (0 == friendsAB.getIsBeenBlack() ? 0 : friendsAB.getIsBeenBlack())));
                getFriendsRepository().updateFriends(new Friends(toUserId, userId, null, (2 == friendsBA.getStatus() ? 2 : friendsBA.getStatus()), (0 == friendsBA.getBlacklist() ? Friends.Blacklist.No : friendsBA.getBlacklist()), 0));
            }
            // ????????????AB??????
            friendsAB = getFriendsRepository().getFriends(userId, toUserId);
            // ??????????????????
            deleteFriendsInfo(userId, toUserId);
            // ??????????????????????????????
            updateOfflineOperation(userId, toUserId);
        }

        return friendsAB;
    }

    /**
     * @param userId
     * @param toUserId
     * @return
     * @Description:???????????????????????????
     **/
    public Friends consoleDeleteBlacklist(Integer userId, Integer toUserId, Integer adminUserId) {
        // ????????????AB??????
        Friends friendsAB = getFriendsRepository().getFriends(userId, toUserId);
        Friends friendsBA = getFriendsRepository().getFriends(toUserId, userId);

        if (null == friendsAB) {
            // ?????????
        } else {
            // ??????????????????
            if (Friends.Blacklist.Yes == friendsAB.getBlacklist() && Friends.Status.Stranger == friendsAB.getStatus()) {
                // ????????????
                getFriendsRepository().deleteFriends(userId, toUserId);
            } else {
                // ????????????
                getFriendsRepository().updateFriends(new Friends(userId, toUserId, null, 2, Friends.Blacklist.No, (0 == friendsAB.getIsBeenBlack() ? 0 : friendsAB.getIsBeenBlack())));
                getFriendsRepository().updateFriends(new Friends(toUserId, userId, null, (2 == friendsBA.getStatus() ? 2 : friendsBA.getStatus()), (0 == friendsBA.getBlacklist() ? Friends.Blacklist.No : friendsBA.getBlacklist()), 0));
            }
            // ????????????AB??????
            friendsAB = getFriendsRepository().getFriends(userId, toUserId);
        }

        ThreadUtil.executeInThread(new Callback() {

            @Override
            public void execute(Object obj) {

                //xmpp????????????
                MessageBean messageBean = new MessageBean();
                messageBean.setType(KXMPPServiceImpl.moveBlacklist);
                messageBean.setFromUserId(adminUserId + "");
                messageBean.setFromUserName("?????????????????????");
                MessageBean beanVo = new MessageBean();
                beanVo.setFromUserId(userId + "");
                beanVo.setFromUserName(getUserManager().getNickName(userId));
                beanVo.setToUserId(toUserId + "");
                beanVo.setToUserName(getUserManager().getNickName(toUserId));
                messageBean.setObjectId(JSONObject.toJSONString(beanVo));
                messageBean.setMessageId(StringUtil.randomUUID());
                try {
                    List<Integer> userIdlist = new ArrayList<Integer>();
                    userIdlist.add(userId);
                    userIdlist.add(toUserId);
                    KXMPPServiceImpl.getInstance().send(messageBean, userIdlist);
                } catch (Exception e) {
                }


            }
        });
        // ??????????????????
        deleteFriendsInfo(userId, toUserId);
        return friendsAB;
    }

    @Override
    public boolean deleteFriends(Integer userId, Integer toUserId) {
        getFriendsRepository().deleteFriends(userId, toUserId);
        getFriendsRepository().deleteFriends(toUserId, userId);
        SKBeanUtils.getTigaseManager().deleteLastMsg(userId.toString(), toUserId.toString());
        SKBeanUtils.getTigaseManager().deleteLastMsg(toUserId.toString(), userId.toString());
        // ??????????????????
        deleteFriendsInfo(userId, toUserId);
        // ??????????????????????????????
        updateOfflineOperation(userId, toUserId);
        return true;
    }

    /**
     * @param userId
     * @param toUserId
     * @return
     * @Description:?????????????????????-xmpp????????????
     **/
    public boolean consoleDeleteFriends(Integer userId, Integer adminUserId, String... toUserIds) {
        for (String strtoUserId : toUserIds) {
            Integer toUserId = Integer.valueOf(strtoUserId);
            getFriendsRepository().deleteFriends(userId, toUserId);
            getFriendsRepository().deleteFriends(toUserId, userId);
            SKBeanUtils.getTigaseManager().deleteLastMsg(userId.toString(), toUserId.toString());
            SKBeanUtils.getTigaseManager().deleteLastMsg(toUserId.toString(), userId.toString());
            ThreadUtil.executeInThread(new Callback() {

                @Override
                public void execute(Object obj) {
                    //????????????????????????????????????
                    MessageBean messageBean = new MessageBean();
                    messageBean.setType(KXMPPServiceImpl.deleteFriends);
                    messageBean.setFromUserId(adminUserId + "");
                    messageBean.setFromUserName("?????????????????????");
                    MessageBean beanVo = new MessageBean();
                    beanVo.setFromUserId(userId + "");
                    beanVo.setFromUserName(getUserManager().getNickName(userId));
                    beanVo.setToUserId(toUserId + "");
                    beanVo.setToUserName(getUserManager().getNickName(toUserId));
                    messageBean.setObjectId(JSONObject.toJSONString(beanVo));
                    messageBean.setMessageId(StringUtil.randomUUID());
                    messageBean.setContent("?????????????????????????????????");
                    messageBean.setMessageId(StringUtil.randomUUID());
                    try {
                        List<Integer> userIdlist = new ArrayList<Integer>();
                        userIdlist.add(userId);
                        userIdlist.add(toUserId);
                        KXMPPServiceImpl.getInstance().send(messageBean, userIdlist);
                    } catch (Exception e) {
                    }
                    // ??????????????????
                    deleteFriendsInfo(userId, toUserId);
                }
            });
        }
        return true;
    }


    @SuppressWarnings("unused")
    @Override
    public JSONMessage followUser(Integer userId, Integer toUserId, Integer fromAddType) {
        final String serviceCode = "08";
        JSONMessage jMessage = null;
        User toUser = getUserManager().getUser(toUserId);
        int toUserType = 0;
        List<Integer> toUserRoles = SKBeanUtils.getRoleManager().getUserRoles(toUserId);
        if (toUserRoles.size() > 0 && null != toUserRoles) {
            if (toUserRoles.contains(2))
                toUserType = 2;
        }
        //???????????????
        if (null == toUser) {
            if (10000 == toUserId)
                return null;
            else
                return JSONMessage.failure("????????????, ???????????????!");

        }

        try {
            User user = getUserManager().getUser(userId);
            int userType = 0;
            List<Integer> userRoles = SKBeanUtils.getRoleManager().getUserRoles(userId);
            if (userRoles.size() > 0 && null != userRoles) {
                if (userRoles.contains(2))
                    userType = 2;
            }

            // ????????????AB??????
            Friends friendsAB = getFriendsRepository().getFriends(userId, toUserId);
            // ????????????BA??????
            Friends friendsBA = getFriendsRepository().getFriends(toUserId, userId);
            // ????????????????????????
            User.UserSettings userSettingsB = getUserManager().getSettings(toUserId);

            // ----------------------------
            // 0 0 0 0 ????????? ??????????????????
            // A B 1 0 ????????? ??????????????????
            // A B 1 1 ??????????????? ??????????????????
            // A B 2 0 ?????? ????????????
            // A B 3 0 ?????? ????????????
            // A B 2 1 ???????????? ????????????
            // A B 3 1 ???????????? ????????????
            // ----------------------------
            // ???AB?????????????????????????????????????????????
            if (null != friendsAB && friendsAB.getIsBeenBlack() == 1) {
                return jMessage = JSONMessage.failure("???????????????");
            }
            if (null == friendsAB || Friends.Status.Stranger == friendsAB.getStatus()) {
                // ????????????????????????
                if (0 == userSettingsB.getAllowAtt()) {
                    jMessage = new JSONMessage(groupCode, serviceCode, "01", "???????????????????????????????????????");
                }
                // ????????????????????????
                else {
                    int statusA = 0;

                    // ?????????????????????????????????????????????????????????????????????????????????
                    if (1 == userSettingsB.getFriendsVerify() && 2 != toUserType) {
                        // ----------------------------
                        // 0 0 0 0 ????????? ??????????????????
                        // B A 1 0 ????????? ??????????????????
                        // B A 1 1 ??????????????? ??????????????????
                        // B A 2 0 ?????? ?????????
                        // B A 3 0 ?????? ?????????
                        // B A 2 1 ???????????? ?????????
                        // B A 3 1 ???????????? ?????????
                        // ----------------------------
                        // ???BA????????????????????????????????????????????????
                        if (null == friendsBA || Friends.Status.Stranger == friendsBA.getStatus()) {
                            statusA = Friends.Status.Attention;
                        } else {
                            statusA = Friends.Status.Friends;

                            getFriendsRepository()
                                    .updateFriends(new Friends(toUserId, user.getUserId(), user.getNickname(), Friends.Status.Friends));
                        }
                    }
                    // ???????????????????????????????????????????????????
                    else {
                        statusA = Friends.Status.Friends;

                        if (null == friendsBA) {
                            getFriendsRepository().saveFriends(new Friends(toUserId, user.getUserId(), user.getNickname(),
                                    Friends.Status.Friends, Friends.Blacklist.No, 0, userRoles, userType, fromAddType));

                            saveFansCount(toUserId);
                        } else
                            getFriendsRepository()
                                    .updateFriends(new Friends(toUserId, user.getUserId(), user.getNickname(), Friends.Status.Friends, userType, userRoles));//??????usertype
                    }

                    if (null == friendsAB) {
                        getFriendsRepository().saveFriends(new Friends(userId, toUserId, toUser.getNickname(), statusA, Friends.Blacklist.No, 0, toUserRoles, toUserType, fromAddType));
                        saveFansCount(toUserId);
                    } else {
                        getFriendsRepository().updateFriends(new Friends(userId, toUserId, toUser.getNickname(), statusA, Friends.Blacklist.No, 0));
                    }

                    if (statusA == Friends.Status.Attention) {
                        HashMap<String, Object> newMap = MapUtil.newMap("type", 1);
                        newMap.put("fromAddType", fromAddType);
                        jMessage = JSONMessage.success("????????????????????????????????????", newMap);
                    } else {
                        HashMap<String, Object> newMap = MapUtil.newMap("type", 2);
                        newMap.put("fromAddType", fromAddType);
                        jMessage = JSONMessage.success("??????????????????????????????", newMap);
                    }

                }
            }
            // ???????????????????????????????????????
            else if (Friends.Blacklist.No == friendsAB.getBlacklist()) {
                if (Friends.Status.Attention == friendsAB.getStatus()) {
                    // ???????????????????????????
                    if (0 == userSettingsB.getFriendsVerify()) {
                        Integer statusA = Friends.Status.Friends;
                        if (null == friendsBA) {
                            getFriendsRepository().saveFriends(new Friends(toUserId, user.getUserId(), user.getNickname(), Friends.Status.Friends, Friends.Blacklist.No, 0, userRoles, userType, fromAddType));
                            saveFansCount(toUserId);
                        } else {
                            getFriendsRepository().updateFriends(new Friends(toUserId, user.getUserId(), user.getNickname(), Friends.Status.Friends));
                        }
                        if (null == friendsAB) {
                            getFriendsRepository().saveFriends(new Friends(userId, toUserId, toUser.getNickname(), statusA, Friends.Blacklist.No, 0, toUserRoles, toUserType, fromAddType));
                            saveFansCount(toUserId);
                        } else {
                            getFriendsRepository().updateFriends(new Friends(userId, toUserId, toUser.getNickname(), statusA, Friends.Blacklist.No, 0));
                        }
                        HashMap<String, Object> newMap = MapUtil.newMap("type", 2);
                        newMap.put("fromAddType", fromAddType);
                        jMessage = JSONMessage.success("??????????????????????????????", newMap);
                    } else if (1 == userSettingsB.getFriendsVerify()) {
                        HashMap<String, Object> newMap = MapUtil.newMap("type", 1);
                        newMap.put("fromAddType", fromAddType);
                        jMessage = JSONMessage.success("????????????????????????????????????", newMap);
                    }
                } else {
                    HashMap<String, Object> newMap = MapUtil.newMap("type", 2);
                    newMap.put("fromAddType", fromAddType);
                    jMessage = JSONMessage.success("??????????????????????????????", newMap);
                }
            }
            // ?????????????????????????????????????????????????????????
            else {
                getFriendsRepository().updateFriends(new Friends(userId, toUserId, toUser.getNickname(), Friends.Blacklist.No));

                jMessage = null;
            }
            // ??????????????????
            deleteFriendsInfo(userId, toUserId);
            // ??????????????????????????????
            updateOfflineOperation(userId, toUserId);
        } catch (Exception e) {
            Log.error("????????????", e);
            jMessage = JSONMessage.failure("????????????");
        }
        return jMessage;
    }

    /**
     * @param userId
     * @param toUserId
     * @Description:??????????????????????????????
     **/
    public void updateOfflineOperation(Integer userId, Integer toUserId) {
        Query<OfflineOperation> query = getDatastore().createQuery(OfflineOperation.class).field("userId").equal(userId).field("tag").equal(KConstants.MultipointLogin.TAG_FRIEND).field("friendId").equal(String.valueOf(toUserId));
        if (null == query.get()) {
            getDatastore().save(new OfflineOperation(userId, KConstants.MultipointLogin.TAG_FRIEND, String.valueOf(toUserId), DateUtil.currentTimeSeconds()));
        } else {
            UpdateOperations<OfflineOperation> ops = getDatastore().createUpdateOperations(OfflineOperation.class);
            ops.set("operationTime", DateUtil.currentTimeSeconds());
            getDatastore().update(query, ops);
        }
    }

    // ??????????????????
    @Override
    public JSONMessage batchFollowUser(Integer userId, String toUserIds) {
        JSONMessage jMessage = null;
        if (StringUtil.isEmpty(toUserIds))
            return null;
        int[] toUserId = StringUtil.getIntArray(toUserIds, ",");
        for (int i = 0; i < toUserId.length; i++) {
            //???????????????
            if (userId == toUserId[i] || 10000 == toUserId[i])
                continue;
            User toUser = getUserManager().getUser(toUserId[i]);
            if (null == toUser)
                continue;
            int toUserType = 0;
            List<Integer> toUserRoles = SKBeanUtils.getRoleManager().getUserRoles(toUserId[i]);
            if (toUserRoles.size() > 0 && null != toUserRoles) {
                if (toUserRoles.contains(2))
                    toUserType = 2;
            }

            try {
                User user = getUserManager().getUser(userId);
                int userType = 0;
                List<Integer> userRoles = SKBeanUtils.getRoleManager().getUserRoles(userId);
                if (userRoles.size() > 0 && null != userRoles) {
                    if (userRoles.contains(2))
                        userType = 2;
                }

                // ????????????AB??????
                Friends friendsAB = getFriendsRepository().getFriends(userId, toUserId[i]);
                // ????????????BA??????
                Friends friendsBA = getFriendsRepository().getFriends(toUserId[i], userId);
                // ????????????????????????
                User.UserSettings userSettingsB = getUserManager().getSettings(toUserId[i]);

                if (null != friendsAB && friendsAB.getIsBeenBlack() == 1) {
//					return jMessage = JSONMessage.failure("???????????????");
//					continue;
                    throw new ServiceException("??????????????????????????????");
                }
                if (null == friendsAB || Friends.Status.Stranger == friendsAB.getStatus()) {
                    // ????????????????????????
                    if (0 == userSettingsB.getAllowAtt()) {
//						jMessage = new JSONMessage(groupCode, serviceCode, "01", "???????????????????????????????????????");
                        continue;
                    }
                    // ????????????????????????
                    else {
                        int statusA = 0;
                        statusA = Friends.Status.Friends;

                        if (null == friendsBA) {
                            getFriendsRepository().saveFriends(new Friends(toUserId[i], user.getUserId(), user.getNickname(),
                                    Friends.Status.Friends, Friends.Blacklist.No, 0, userRoles, userType, 4));

                            saveFansCount(toUserId[i]);
                        } else
                            getFriendsRepository()
                                    .updateFriends(new Friends(toUserId[i], user.getUserId(), user.getNickname(), Friends.Status.Friends));

                        if (null == friendsAB) {
                            getFriendsRepository().saveFriends(new Friends(userId, toUserId[i], toUser.getNickname(), statusA, Friends.Blacklist.No, 0, toUserRoles, toUserType, 4));
                            saveFansCount(toUserId[i]);
                        } else {
                            getFriendsRepository().updateFriends(new Friends(userId, toUserId[i], toUser.getNickname(), statusA, Friends.Blacklist.No, 0));
                        }

                    }
                }
                // ???????????????????????????????????????
                else if (Friends.Blacklist.No == friendsAB.getBlacklist()) {
                    if (Friends.Status.Attention == friendsAB.getStatus()) {
                        // ?????????????????????????????????
                        getFriendsRepository().updateFriends(new Friends(userId, toUserId[i], toUser.getNickname(), Friends.Status.Friends, toUserType, toUserRoles));
                        // ??????????????????
                        getFriendsRepository().saveFriends(new Friends(toUserId[i], user.getUserId(), user.getNickname(),
                                Friends.Status.Friends, Friends.Blacklist.No, 0, userRoles, "", userType));
//						continue;
                    }
                } else {
                    // ?????????????????????????????????????????????????????????
                    getFriendsRepository().updateFriends(new Friends(userId, toUserId[i], toUser.getNickname(), Friends.Blacklist.No));
                    jMessage = null;
                }
                notify(userId, toUserId[i]);
                jMessage = JSONMessage.success();
                // ??????????????????
                deleteAddressFriendsInfo(userId, toUserId[i]);
                // ??????????????????????????????
                updateOfflineOperation(userId, toUserId[i]);
            } catch (Exception e) {
                Log.error("???????????????????????????", e.getMessage());
                jMessage = JSONMessage.failure(e.getMessage());
            }
        }
        return jMessage;
    }


    /**
     * @param userId
     * @param addressBook<userid ??????id, toRemark ?????? >
     * @return
     * @Description:?????????????????????????????????
     **/
    public JSONMessage autofollowUser(Integer userId, Map<String, String> addressBook) {
        final String serviceCode = "08";
        Integer toUserId = Integer.valueOf(addressBook.get("toUserId"));
        String toRemark = addressBook.get("toRemark");

//		final String serviceCode = "08";
        JSONMessage jMessage = null;
        User toUser = getUserManager().getUser(toUserId);
        int toUserType = 0;
        List<Integer> toUserRoles = SKBeanUtils.getRoleManager().getUserRoles(toUserId);
        if (toUserRoles.size() > 0 && null != toUserRoles) {
            if (toUserRoles.contains(2))
                toUserType = 2;
        }
        //???????????????
        if (10000 == toUser.getUserId())
            return null;
        try {
            User user = getUserManager().getUser(userId);
            int userType = 0;
            List<Integer> userRoles = SKBeanUtils.getRoleManager().getUserRoles(userId);
            if (userRoles.size() > 0 && null != userRoles) {
                if (userRoles.contains(2))
                    userType = 2;
            }

            // ????????????AB??????
            Friends friendsAB = getFriendsRepository().getFriends(userId, toUserId);
            // ????????????BA??????
            Friends friendsBA = getFriendsRepository().getFriends(toUserId, userId);
            // ????????????????????????
            User.UserSettings userSettingsB = getUserManager().getSettings(toUserId);

            if (null != friendsAB && friendsAB.getIsBeenBlack() == 1) {
                return jMessage = JSONMessage.failure("???????????????");
            }
            if (null == friendsAB || Friends.Status.Stranger == friendsAB.getStatus()) {
                // ????????????????????????
                if (0 == userSettingsB.getAllowAtt()) {
                    jMessage = new JSONMessage(groupCode, serviceCode, "01", "???????????????????????????????????????");
                }
                // ????????????????????????
                else {
                    int statusA = 0;
                    // ???????????????????????????????????????????????????
//						else {
                    statusA = Friends.Status.Friends;

                    if (null == friendsBA) {
                        getFriendsRepository().saveFriends(new Friends(toUserId, user.getUserId(), user.getNickname(),
                                Friends.Status.Friends, Friends.Blacklist.No, 0, userRoles, "", userType));

                        saveFansCount(toUserId);
                    } else
                        getFriendsRepository()
                                .updateFriends(new Friends(toUserId, user.getUserId(), user.getNickname(), Friends.Status.Friends));
//						}

                    if (null == friendsAB) {
                        getFriendsRepository().saveFriends(new Friends(userId, toUserId, toUser.getNickname(), statusA, Friends.Blacklist.No, 0, toUserRoles, toRemark, toUserType));
                        saveFansCount(toUserId);
                    } else {
                        getFriendsRepository().updateFriends(new Friends(userId, toUserId, toUser.getNickname(), statusA, Friends.Blacklist.No, 0));
                    }

                }
            }
            // ???????????????????????????????????????
            else if (Friends.Blacklist.No == friendsAB.getBlacklist()) {
                if (Friends.Status.Attention == friendsAB.getStatus()) {
                    // ?????????????????????????????????
                    getFriendsRepository().updateFriends(new Friends(userId, toUserId, toUser.getNickname(), Friends.Status.Friends, toUserType, toUserRoles));
                    // ??????????????????
                    getFriendsRepository().saveFriends(new Friends(toUserId, user.getUserId(), user.getNickname(),
                            Friends.Status.Friends, Friends.Blacklist.No, 0, userRoles, "", userType));
                }
            } else {
                // ?????????????????????????????????????????????????????????
                getFriendsRepository().updateFriends(new Friends(userId, toUserId, toUser.getNickname(), Friends.Blacklist.No));
                jMessage = null;
            }
            notify(userId, toUserId);
            // ??????????????????
            deleteFriendsInfo(userId, toUserId);
            jMessage = JSONMessage.success();
        } catch (Exception e) {
            Log.error("????????????", e);
            jMessage = JSONMessage.failure("????????????");
        }
        return jMessage;
    }

    public void notify(Integer userId, Integer toUserId) {
        ThreadUtil.executeInThread(new Callback() {
            @Override
            public void execute(Object obj) {
                MessageBean messageBean = new MessageBean();
                messageBean.setType(KXMPPServiceImpl.batchAddFriend);
                messageBean.setFromUserId(String.valueOf(userId));
                messageBean.setFromUserName(SKBeanUtils.getUserManager().getNickName(userId));
                messageBean.setToUserId(String.valueOf(toUserId));
                messageBean.setToUserName(SKBeanUtils.getUserManager().getNickName(toUserId));
                messageBean.setContent(toUserId);
                messageBean.setMsgType(0);// ????????????
                messageBean.setMessageId(StringUtil.randomUUID());
                try {
                    KXMPPServiceImpl.getInstance().send(messageBean);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public Friends getFriends(int userId, int toUserId) {
        return getFriendsRepository().getFriends(userId, toUserId);
    }

    public void getFriends(int userId, String... toUserIds) {
        for (String strToUserId : toUserIds) {
            Integer toUserId = Integer.valueOf(strToUserId);
            Friends friends = getFriendsRepository().getFriends(userId, toUserId);
            if (null == friends)
                throw new ServiceException("????????????????????????");

        }
//		return getFriendsRepository().getFriends(userId, toUserId);
//		return getFriendsRepository().getFriends(userId, toUserId);
    }

    public List<Friends> getFansList(Integer userId) {

        List<Friends> result = getEntityListsByKey("userId", userId);
        result.forEach(friends -> {
            User user = getUserManager().getUser(friends.getToUserId());

            friends.setToNickname(user.getNickname());
        });


        return result;
    }


    @Override
    public Friends getFriends(Friends p) {
        return getFriendsRepository().getFriends(p.getUserId(), p.getToUserId());
    }

    @Override
    public List<Integer> getFriendsIdList(int userId) {
        List<Integer> result = Lists.newArrayList();

        try {
            List<Friends> friendsList = getFriendsRepository().queryFriends(userId);
            friendsList.forEach(friends -> {
                result.add(friends.getToUserId());
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    @Override
    public List<Friends> queryBlacklist(Integer userId, int pageIndex, int pageSize) {
        return getFriendsRepository().queryBlacklist(userId, pageIndex, pageSize);
    }

    public PageVO queryBlacklistWeb(Integer userId, int pageIndex, int pageSize) {
        return getFriendsRepository().queryBlacklistWeb(userId, pageIndex, pageSize);
    }

    @Override
    public List<Integer> queryFansId(Integer userId) {
        return getFriendsRepository().queryFansId(userId);
    }

    /**
     * ???????????????????????? ?????????
     *
     * @return
     */
    public boolean getFriendIsNoPushMsg(Integer userId, Integer toUserId) {
        DBObject query = new BasicDBObject("userId", userId).append("toUserId", toUserId);
        query.put("offlineNoPushMsg", 1);
        Object field = queryOneField("offlineNoPushMsg", query);
        return null != field;
    }

    @Override
    public List<Friends> queryFollow(Integer userId, int status) {
        logger.info("queryFollow userId=====>{}",userId);
        List<Friends> userfriends = SKBeanUtils.getRedisService().getFriendsList(userId);
        logger.info("queryFollow userfriends=====>{}-status{}",userfriends.size(),status);
        if (null != userfriends && userfriends.size() > 0) {
            return userfriends;
        } else {
            if (0 == status)
                status = 2;  //??????
            List<Friends> result = getFriendsRepository().queryFollow(userId, status);
            logger.info("queryFollow result=====>{}",result.size());
            SKBeanUtils.getRedisService().saveFriendsList(userId, result);
            Iterator<Friends> iter = result.iterator();
            while (iter.hasNext()) {
                Friends friends = iter.next();
                User user = getUserManager().getUser(friends.getToUserId());
                logger.info("queryFollow user=====>{}",user);
                if (null == user) {
                    iter.remove();
                    deleteFansAndFriends(friends.getToUserId());
                    logger.info("queryFollow deleteFansAndFriends=====>{}",user.getUsername());
                    continue;
                }
                friends.setToNickname(user.getNickname());
            }
            logger.info("queryFollow end=====>{}",result.size());
            return result;

        }
    }


    public PageResult<Friends> consoleQueryFollow(Integer userId, Integer toUserId, int status, int page, int limit) {
        PageResult<Friends> result = new PageResult<Friends>();
        result = getFriendsRepository().consoleQueryFollow(userId, toUserId, status, page, limit);
        Iterator<Friends> iter = result.getData().iterator();
        while (iter.hasNext()) {
            Friends friends = iter.next();
            User user = getUserManager().getUser(friends.getToUserId());
            friends.setNickname(getUserManager().getNickName(userId));
            if (null == user) {
                iter.remove();
                deleteFansAndFriends(friends.getToUserId());
                continue;
            }
            friends.setToNickname(user.getNickname());
        }
        return result;
    }


    @Override
    public List<Integer> queryFollowId(Integer userId) {
        return getFriendsRepository().queryFollowId(userId);
    }

    @Override
    public List<Friends> queryFriends(Integer userId) {
        List<Friends> result = getFriendsRepository().queryFriends(userId);

        for (Friends friends : result) {
            User toUser = getUserManager().getUser(friends.getToUserId());
            if (null == toUser) {
                deleteFansAndFriends(friends.getToUserId());
                continue;
            }
            friends.setToNickname(toUser.getNickname());
            //friends.setCompanyId(toUser.getCompanyId());
        }

        return result;
    }


    @Override   //???????????????userId ??????????????????userId
    public List<Integer> friendsAndAttentionUserId(Integer userId, String type) {
        List<Friends> result = new ArrayList<Friends>();
        if ("friendList".equals(type) || "blackList".equals(type)) {  //???????????????userId ??????????????????userId
            result = getFriendsRepository().friendsOrBlackList(userId, type);
        } else {
            throw new ServiceException("?????????????????????");
        }
        List<Integer> userIds = new ArrayList<Integer>();
        for (Friends friend : result) {
            userIds.add(friend.getToUserId());
        }
        return userIds;
    }

    @Override
    public PageVO queryFriends(Integer userId, int status, String keyword, int pageIndex, int pageSize) {
        Query<Friends> q = getDatastore().createQuery(getEntityClass()).field("userId").equal(userId);
        if (0 < status)
            q.filter("status", status);
        if (!StringUtil.isEmpty(keyword)) {
            //q.field("toNickname").containsIgnoreCase(keyword);
            q.or(q.criteria("toNickname").containsIgnoreCase(keyword),
                    q.criteria("remarkName").containsIgnoreCase(keyword));
        }
        long total = q.countAll();
        List<Friends> pageData = q.offset(pageIndex * pageSize).limit(pageSize).asList();
        for (Friends friends : pageData) {
            User toUser = getUserManager().getUser(friends.getToUserId());
            if (null == toUser) {
                deleteFansAndFriends(friends.getToUserId());
                continue;
            }
            if (toUser.getUserId() == 10000) {
                continue;
            }
            friends.setToNickname(toUser.getNickname());
            //friends.setCompanyId(toUser.getCompanyId());
        }
        return new PageVO(pageData, total, pageIndex, pageSize);
    }

    public List<Friends> queryFriendsList(Integer userId, int status, int pageIndex, int pageSize) {
        Query<Friends> q = getDatastore().createQuery(getEntityClass()).field("userId").equal(userId);
        if (0 < status)
            q.filter("status", status);
			/*if(!StringUtil.isEmpty(keyword)){
				q.or(q.criteria("nickname").containsIgnoreCase(keyword),
						q.criteria("telephone").contains(keyword));
			}*/
        List<Friends> pageData = q.offset(pageIndex * pageSize).limit(pageSize).asList();
        for (Friends friends : pageData) {
            User toUser = getUserManager().getUser(friends.getToUserId());
            if (null == toUser) {
                deleteFansAndFriends(friends.getToUserId());
                continue;
            }
            friends.setToNickname(toUser.getNickname());
            //friends.setCompanyId(toUser.getCompanyId());
        }
        return pageData;
    }


    /**
     * ????????????
     */
    @Override
    public boolean unfollowUser(Integer userId, Integer toUserId) {
        // ??????????????????
        getFriendsRepository().deleteFriends(userId, toUserId);
        // ??????????????????????????????
        updateOfflineOperation(userId, toUserId);
        return true;
    }

    @Override
    public Friends updateRemark(int userId, int toUserId, String remarkName, String describe) {
//		Friends friends = new Friends(userId, toUserId);
//		friends.setRemarkName(remarkName);
//		return getFriendsRepository().updateFriends(friends);
        return getFriendsRepository().updateFriendRemarkName(userId, toUserId, remarkName, describe);
    }


    @Override
    public void deleteFansAndFriends(int userId) {
        getFriendsRepository().deleteFriends(userId);
    }

    /* (non-Javadoc)
     * @see cn.xyz.mianshi.service.FriendsManager#newFriendList(int,int,int)
     */
    @Override
    public List<NewFriends> newFriendList(int userId, int pageIndex, int pageSize) {

        Query<NewFriends> query = getDatastore().createQuery(NewFriends.class);
        query.filter("userId", userId);
        query.or(query.criteria("userId").equal(userId),
                query.criteria("toUserId").equal(userId));

        List<NewFriends> pageData = query.order("-modifyTime").offset(pageIndex * pageSize).limit(pageSize).asList();
        Friends friends = null;
        for (NewFriends newFriends : pageData) {
            friends = getFriends(newFriends.getUserId(), newFriends.getToUserId());
            newFriends.setToNickname(getUserManager().getNickName(newFriends.getToUserId()));
			
			/*if(userId==newFriends.getToUserId()){
				friends=getFriends(newFriends.getToUserId(), newFriends.getUserId());
				newFriends.setToNickname(getUserManager().getNickName(newFriends.getUserId()));
			}
			else{
				friends=getFriends(newFriends.getUserId(), newFriends.getToUserId());
				newFriends.setToNickname(getUserManager().getNickName(newFriends.getToUserId()));
			}*/

            if (null != friends)
                newFriends.setStatus(friends.getStatus());
        }
        //long total=query.countAll();
        //return new PageVO(pageData, total, pageIndex, pageSize);

        return pageData;

    }

    @SuppressWarnings("deprecation")
    public PageVO newFriendListWeb(int userId, int pageIndex, int pageSize) {

        Query<NewFriends> query = getDatastore().createQuery(NewFriends.class);
        query.filter("userId", userId);
        query.or(query.criteria("userId").equal(userId),
                query.criteria("toUserId").equal(userId));
        List<NewFriends> pageData = query.order("-modifyTime").offset(pageIndex * pageSize).limit(pageSize).asList();
        Friends friends = null;
        for (NewFriends newFriends : pageData) {
            friends = getFriends(newFriends.getUserId(), newFriends.getToUserId());
            newFriends.setToNickname(getUserManager().getNickName(newFriends.getToUserId()));
            if (null != friends)
                newFriends.setStatus(friends.getStatus());
        }
        return new PageVO(pageData, query.count(), pageIndex, pageSize);
    }

    /* ?????????????????????????????????????????????????????????
     * type = 0  ??????????????? ,type = 1  ???????????? ,type = 2  ????????????
     */
    @Override
    public Friends updateOfflineNoPushMsg(int userId, int toUserId, int offlineNoPushMsg, int type) {
        Query<Friends> q = getDatastore().createQuery(getEntityClass()).field("userId").equal(userId).field("toUserId").equal(toUserId);
        UpdateOperations<Friends> ops = getDatastore().createUpdateOperations(getEntityClass());
        switch (type) {
            case 0:
                ops.set("offlineNoPushMsg", offlineNoPushMsg);
                break;
            case 1:
                ops.set("isOpenSnapchat", offlineNoPushMsg);
                break;
            case 2:
                ops.set("openTopChatTime", (offlineNoPushMsg == 0 ? 0 : DateUtil.currentTimeSeconds()));
                break;
            default:
                break;
        }
        // ??????????????????????????????xmpp??????
        if (getUserManager().isOpenMultipleDevices(userId))
            getUserManager().multipointLoginUpdateUserInfo(userId, getUserManager().getNickName(userId), toUserId, getUserManager().getNickName(toUserId), 1);
        return getDatastore().findAndModify(q, ops);
    }


    /**
     * ??????????????????      ??????????????????????????????????????????????????????????????????????????????
     *
     * @param startDate
     * @param endDate
     * @param counType  ????????????   1: ??????????????????      2:???????????????       3.???????????????   4.?????????????????? (??????)
     */
    public List<Object> getAddFriendsCount(String startDate, String endDate, short timeUnit) {

        List<Object> countData = new ArrayList<>();

        long startTime = 0; //?????????????????????

        long endTime = 0; //?????????????????????,?????????????????????

        /**
         * ??????????????????????????????????????????????????????????????????????????? ; ????????????????????????????????????????????????????????????????????????;
         * ??????????????????????????????????????????????????????????????????0???
         */
        long defStartTime = timeUnit == 4 ? DateUtil.getTodayMorning().getTime() / 1000
                : timeUnit == 3 ? DateUtil.getLastMonth().getTime() / 1000 : DateUtil.getLastYear().getTime() / 1000;

        startTime = StringUtil.isEmpty(startDate) ? defStartTime : DateUtil.toDate(startDate).getTime() / 1000;
        endTime = StringUtil.isEmpty(endDate) ? DateUtil.currentTimeSeconds() : DateUtil.toDate(endDate).getTime() / 1000;

        BasicDBObject queryTime = new BasicDBObject("$ne", null);

        if (startTime != 0 && endTime != 0) {
            queryTime.append("$gt", startTime);
            queryTime.append("$lt", endTime);
        }

        BasicDBObject query = new BasicDBObject("createTime", queryTime);

        //????????????????????????
        DBCollection collection = SKBeanUtils.getDatastore().getCollection(getEntityClass());

        String mapStr = "function Map() { "
                + "var date = new Date(this.createTime*1000);"
                + "var year = date.getFullYear();"
                + "var month = (\"0\" + (date.getMonth()+1)).slice(-2);"  //month ???0?????????????????????1
                + "var day = (\"0\" + date.getDate()).slice(-2);"
                + "var hour = (\"0\" + date.getHours()).slice(-2);"
                + "var minute = (\"0\" + date.getMinutes()).slice(-2);"
                + "var dateStr = date.getFullYear()" + "+'-'+" + "(parseInt(date.getMonth())+1)" + "+'-'+" + "date.getDate();";

        if (timeUnit == 1) { // counType=1: ??????????????????
            mapStr += "var key= year + '-'+ month;";
        } else if (timeUnit == 2) { // counType=2:???????????????
            mapStr += "var key= year + '-'+ month + '-' + day;";
        } else if (timeUnit == 3) { //counType=3 :???????????????
            mapStr += "var key= year + '-'+ month + '-' + day + '  ' + hour +' : 00';";
        } else if (timeUnit == 4) { //counType=4 :??????????????????
            mapStr += "var key= year + '-'+ month + '-' + day + '  ' + hour + ':'+ minute;";
        }

        mapStr += "emit(key,1);}";

        String reduce = "function Reduce(key, values) {" +
                "return Array.sum(values);" +
                "}";
        MapReduceCommand.OutputType type = MapReduceCommand.OutputType.INLINE;//
        MapReduceCommand command = new MapReduceCommand(collection, mapStr, reduce, null, type, query);

        int i = 0;
        MapReduceOutput mapReduceOutput = null;
        while (i < 5) {
            i++;
            try {
                mapReduceOutput = collection.mapReduce(command);
                break;
            } catch (MongoSocketWriteException e) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                logger.info("retry getAddFriendsCount mapReduce:{}", i);
            }
        }
        if (null == mapReduceOutput) return countData;
        Iterable<DBObject> results = mapReduceOutput.results();
        Map<String, Double> map = new HashMap<String, Double>();
        for (Iterator iterator = results.iterator(); iterator.hasNext(); ) {
            DBObject obj = (DBObject) iterator.next();

            map.put((String) obj.get("_id"), (Double) obj.get("value"));
            countData.add(JSON.toJSON(map));
            map.clear();

        }

        return countData;
    }

    // ???????????????????????????
    public PageResult<DBObject> chardRecord(Integer sender, Integer receiver, Integer page, Integer limit) {
        DBCollection dbCollection = getTigaseDatastore().getDB().getCollection("shiku_msgs");
        BasicDBObject query = new BasicDBObject();
        BasicDBList queryOr = new BasicDBList();
        if (0 != sender) {
            queryOr.add(new BasicDBObject("sender", sender).append("receiver", receiver).append("direction", 0));
        }
        if (0 != receiver) {
            queryOr.add(new BasicDBObject("sender", receiver).append("receiver", sender).append("direction", 0));
        }
        query.append(MongoOperator.OR, queryOr);

        long total = dbCollection.count(query);
        List<DBObject> pageData = Lists.newArrayList();

        DBCursor cursor = dbCollection.find(query).sort(new BasicDBObject("_id", -1)).skip((page - 1) * limit).limit(limit);

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
//				dbObj.put("content", dbObj.get("content"));
            } catch (Exception e) {
                dbObj.put("content", "--");
            }
            pageData.add(dbObj);
        }

        result.setData(pageData);
        result.setCount(total);
        return result;
    }

    /**
     * @param sender
     * @param receiver
     * @Description:????????????????????????????????????
     **/
    public void delFriendsChatRecord(String... messageIds) {
        for (String messageId : messageIds) {
            DBCollection dbCollection = getTigaseDatastore().getDB().getCollection("shiku_msgs");
            BasicDBObject query = new BasicDBObject();
            query.put("messageId", messageId);
            dbCollection.remove(query);
        }
    }

    /**
     * @param userId
     * @param request
     * @param response
     * @return
     * @Description: ??????????????????
     **/
    public Workbook exprotExcelFriends(Integer userId, HttpServletRequest request, HttpServletResponse response) {

        String name = getUserManager().getNickName(userId) + "???????????????";

        String fileName = "friends.xlsx";

        List<Friends> friends;

        List<Friends> friendsList = SKBeanUtils.getRedisService().getFriendsList(userId);
        if (null != friendsList && friendsList.size() > 0) {
            friends = friendsList;
        } else {
            friends = SKBeanUtils.getFriendsRepository().allFriendsInfo(userId);
        }
        List<String> titles = Lists.newArrayList();
        titles.add("toUserId");
        titles.add("toNickname");
        titles.add("remarkName");
        titles.add("telephone");
        titles.add("status");
        titles.add("blacklist");
        titles.add("isBeenBlack");
        titles.add("offlineNoPushMsg");
        titles.add("createTime");

        List<Map<String, Object>> values = Lists.newArrayList();
        for (Friends friend : friends) {
            // ????????????10000????????????
            if (10000 == friend.getToUserId())
                continue;
            Map<String, Object> map = Maps.newConcurrentMap();
            map.put("toUserId", friend.getToUserId());
            map.put("toNickname", friend.getToNickname());
            map.put("telephone", getUserManager().getUser(friend.getToUserId()).getPhone());
            map.put("status", friend.getStatus() == -1 ? "?????????" : friend.getStatus() == 2 ? "??????" : "??????");
            map.put("blacklist", friend.getBlacklist() == 1 ? "???" : "???");
            map.put("isBeenBlack", friend.getIsBeenBlack() == 1 ? "???" : "???");
            map.put("offlineNoPushMsg", friend.getBlacklist() == 1 ? "???" : "???");
            map.put("createTime", DateUtil.strToDateTime(friend.getCreateTime()));
            values.add(map);
        }

        Workbook workBook = ExcelUtil.generateWorkbook(name, "xlsx", titles, values);
        response.reset();
        try {
            response.setHeader("Content-Disposition",
                    "attachment;filename=" + new String(fileName.getBytes(), "utf-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return workBook;
    }

    /**
     * @param userId
     * @param toUserId
     * @param type     -1 ?????????????????? 1???????????????  2 ??????????????????   3 ?????????????????????
     * @return
     * @Description:???????????????????????????????????????
     **/
    public boolean isAddressBookOrFriends(Integer userId, Integer toUserId, int type) {
        boolean flag = false;
        switch (type) {
            case -1:
                break;
            case 1:
                flag = !flag;
                break;
            case 2:
                List<Integer> friendsUserIdsList;
                List<Integer> allFriendsUserIdsList = SKBeanUtils.getRedisService().getFriendsUserIdsList(userId);
                if (null != allFriendsUserIdsList && allFriendsUserIdsList.size() > 0)
                    friendsUserIdsList = allFriendsUserIdsList;
                else {
                    List<Integer> friendsUserIdsDB = SKBeanUtils.getFriendsManager().queryFansId(userId);
                    friendsUserIdsList = friendsUserIdsDB;
                    SKBeanUtils.getRedisService().saveFriendsUserIdsList(userId, friendsUserIdsList);
                }
                flag = friendsUserIdsList.contains(toUserId);
                break;
            case 3:
                List<Integer> addressBookUserIdsList;
                List<Integer> allAddressBookUserIdsList = SKBeanUtils.getRedisService().getAddressBookFriendsUserIds(userId);
                if (null != allAddressBookUserIdsList && allAddressBookUserIdsList.size() > 0)
                    addressBookUserIdsList = allAddressBookUserIdsList;
                else {
                    List<Integer> AddressBookUserIdsDB = SKBeanUtils.getAddressBookManger().getAddressBookUserIds(userId);
                    addressBookUserIdsList = AddressBookUserIdsDB;
                    SKBeanUtils.getRedisService().saveAddressBookFriendsUserIds(userId, addressBookUserIdsList);
                }
                flag = addressBookUserIdsList.contains(toUserId);
                break;
            default:
                break;
        }
        return flag;
    }
}
