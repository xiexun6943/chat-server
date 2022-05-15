package cn.xyz.mianshi.service.impl;

import cn.xyz.commons.utils.*;
import cn.xyz.mianshi.model.PageResult;
import cn.xyz.mianshi.model.UserExample;
import cn.xyz.mianshi.opensdk.entity.SkOpenAccount;
import cn.xyz.mianshi.utils.SKBeanUtils;
import cn.xyz.mianshi.vo.User;
import cn.xyz.mianshi.vo.UserOperationLog;
import cn.xyz.threadpool.LoggerThreadPoolManager;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserOperationLogImpl extends MongoRepository<UserOperationLog, ObjectId>{
    protected static ConcurrentLinkedQueue<UserOperationLog> queue = new ConcurrentLinkedQueue<>();
    @Override
    public Datastore getDatastore() {
        return SKBeanUtils.getLocalSpringBeanManager().getDatastore();
    }

    @Override
    public Class<UserOperationLog> getEntityClass() {
        return UserOperationLog.class;
    }

    /** @Description:（用户IP日志查询）
     * @param userId
     * @param page
     * @param limit
     * @return
     **/
    public PageResult<UserOperationLog> findOperationLog(int userId, Integer logtype, int page, int limit, String startDate, String endDate, String ip,String telephone,String nickName){
        PageResult<UserOperationLog> result = new PageResult<UserOperationLog>();
        Query<UserOperationLog> query = getDatastore().createQuery(getEntityClass()).order("-dayTime");
        if (null != logtype) {
            query.filter("logtype", logtype);
        }
        if(0 != userId)
            query.filter("userId", userId);
        if(!StringUtil.isEmpty(ip))
            query.filter("ip", ip);
        if(!StringUtil.isEmpty(telephone))
            query.filter("telephone", telephone);
        if(!StringUtil.isEmpty(startDate) && !StringUtil.isEmpty(endDate)){
            long startTime = 0; //开始时间（秒）
            long endTime = 0; //结束时间（秒）,默认为当前时间
            startTime = StringUtil.isEmpty(startDate) ? 0 : DateUtil.toDate(startDate).getTime()/1000;
            endTime = StringUtil.isEmpty(endDate) ? DateUtil.currentTimeSeconds() : DateUtil.toDate(endDate).getTime()/1000;
            long formateEndtime = DateUtil.getOnedayNextDay(endTime,1,0);
            query.field("dayTime").greaterThan(startTime).field("dayTime").lessThanOrEq(formateEndtime);
        }
        if (!StringUtil.isEmpty(nickName)) {
            // Integer 最大值2147483647
            boolean flag = NumberUtil.isNum(nickName);
            if(flag){
                Integer length = nickName.length();
                if(length > 9){
                    query.or(query.criteria("nickName").containsIgnoreCase(nickName),
                            query.criteria("telephone").containsIgnoreCase(nickName));
                }else{
                    query.or(query.criteria("nickName").containsIgnoreCase(nickName),
                            query.criteria("telephone").containsIgnoreCase(nickName),
                            query.criteria("_id").equal(Integer.valueOf(nickName)));
                }
            }else{
                query.or(query.criteria("nickName").containsIgnoreCase(nickName),
                        query.criteria("telephone").containsIgnoreCase(nickName));
            }
        }
        // 排序、分页
        List<UserOperationLog> recordList = query.asList(pageFindOption(page, limit, 1));
        result.setCount(query.count());
        result.setData(recordList);
        return result;
    }


    public void updateUserOperationLog(User user, HttpServletRequest request,Integer logtype) {
        UserOperationLog loginLog = new UserOperationLog();
        String ip= NetworkUtil.getIpAddress(request);
        loginLog.setActiontime(new Date());
        loginLog.setUserId(user.getUserId());
        loginLog.setBrowser(SKBeanUtils.getLocalSpringBeanManager().getIpAddressManager().getRequestBrowser(request));
        loginLog.setIp(ip);
        loginLog.setModel(user.getModel());
        loginLog.setProvince(SKBeanUtils.getLocalSpringBeanManager().getIpAddressManager().getProvince(ip));
        loginLog.setArea(SKBeanUtils.getLocalSpringBeanManager().getIpAddressManager().getSubdivision(ip));
        loginLog.setNickName(user.getNickname());
        loginLog.setDayTime(DateUtil.currentTimeSeconds());
        loginLog.setLogtype(logtype);
        loginLog.setTelephone(user.getTelephone());
        loginLog.setOperatingSystem(DeviceUtils.isMobileDevice(request).get("deviceName"));
        SKBeanUtils.getRedisService().savegetUserOperatioIp(user.getUserId(),loginLog);
//        RLock lock = SKBeanUtils.getRedisService().getLock(user.getUserId().toString());
//        try{
//            lock.lock();
//            boolean res = lock.tryLock(3, 5, TimeUnit.SECONDS);
//            if(res){ //成功
//                Query<UserOperationLog> q = getDatastore().createQuery(getEntityClass()).field("userId")
//                        .equal(user.getUserId());
//                if(null== q.get()){
//                    getDatastore().save(loginLog);
//                }else{
//                    UpdateOperations<UserOperationLog> ops = getDatastore().createUpdateOperations(UserOperationLog.class);
//                    if(null!=ip){
//                        ops.set("ip",ip);
//                    }
//                    if(null!=user.getTelephone()){
//                        ops.set("telephone",user.getTelephone());
//                    }
//                    if(null!=loginLog.getArea()){
//                        ops.set("area",loginLog.getArea());
//                    }
//                    if(null!=loginLog.getNickName()){
//                        ops.set("nickName", loginLog.getNickName());
//                    }
//                    if(null!=loginLog.getProvince()){
//                        ops.set("province", loginLog.getProvince());
//                    }
//                    ops.set("actiontime",loginLog.getActiontime());
//                    ops.set("dayTime", loginLog.getDayTime());
//                    getDatastore().findAndModify(q, ops);
//                }
//            }
//            SKBeanUtils.getRedisService().savegetUserOperatioIp(user.getUserId(),loginLog);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            lock.unlock();
//        }
        Query<UserOperationLog> q = getDatastore().createQuery(getEntityClass()).field("userId")
                        .equal(user.getUserId()).field("logtype").equal(logtype);
                if(null== q.get()){
                    getDatastore().save(loginLog);
                }else {
                    UpdateOperations<UserOperationLog> ops = getDatastore().createUpdateOperations(UserOperationLog.class);
                    if (null != ip) {
                        ops.set("ip", ip);
                    }
                    if (null != user.getTelephone()) {
                        ops.set("telephone", user.getTelephone());
                    }
                    if (null != loginLog.getArea()) {
                        ops.set("area", loginLog.getArea());
                    }
                    if (null != loginLog.getNickName()) {
                        ops.set("nickName", loginLog.getNickName());
                    }
                    if (null != loginLog.getProvince()) {
                        ops.set("province", loginLog.getProvince());
                    }
                    ops.set("actiontime", loginLog.getActiontime());
                    ops.set("dayTime", loginLog.getDayTime());
                    getDatastore().findAndModify(q, ops);
                }
    }

    public void saveUserOperationLog(User user, HttpServletRequest request, int logtype) {
        logger.info("用户"+user.getUserId()+"-"+user.getUsername()+"-"+user.getTelephone()+"保存登陆日志start");
        UserOperationLog loginLog = new UserOperationLog();
        String ip= NetworkUtil.getIpAddress(request);
        loginLog.setActiontime(new Date());
        loginLog.setUserId(user.getUserId());
        loginLog.setBrowser(SKBeanUtils.getLocalSpringBeanManager().getIpAddressManager().getRequestBrowser(request));
        loginLog.setIp(ip);
        loginLog.setModel(user.getModel());
        loginLog.setProvince(SKBeanUtils.getLocalSpringBeanManager().getIpAddressManager().getProvince(ip));
        loginLog.setArea(SKBeanUtils.getLocalSpringBeanManager().getIpAddressManager().getSubdivision(ip));
        loginLog.setNickName(user.getNickname());
        loginLog.setDayTime(DateUtil.currentTimeSeconds());
        loginLog.setLogtype(logtype);
        loginLog.setTelephone(user.getTelephone());
        loginLog.setOperatingSystem(DeviceUtils.isMobileDevice(request).get("deviceName"));
        RLock lock = SKBeanUtils.getRedisService().getLock(String.format(user.getUserId().toString(),logtype));
        try{
            // 1. 最常见的使用方法
            lock.lock();
            // 2. 支持过期解锁功能,10秒钟以后自动解锁, 无需调用unlock方法手动解锁
            //lock.lock(10, TimeUnit.SECONDS);
            // 3. 尝试加锁，最多等待3秒，上锁以后10秒自动解锁
//            boolean res = lock.tryLock(3, 5, TimeUnit.SECONDS);
//            if(res){ //成功
            SKBeanUtils.getRedisService().savegetUserOperatioIp(user.getUserId(),loginLog);
            getDatastore().save(loginLog);
//            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
        logger.info("用户"+user.getUserId()+"-"+user.getUsername()+"-"+user.getTelephone()+"保存登陆日志end");

    }


    public void saveUserRegisterLog(UserExample example, HttpServletRequest request, int logtype) {
        UserOperationLog loginLog = new UserOperationLog();
        String ip= NetworkUtil.getIpAddress(request);
        loginLog.setActiontime(new Date());
        loginLog.setUserId(example.getUserId());
        loginLog.setBrowser(SKBeanUtils.getLocalSpringBeanManager().getIpAddressManager().getRequestBrowser(request));
        loginLog.setIp(ip);
        loginLog.setModel(example.getModel());
        loginLog.setProvince(SKBeanUtils.getLocalSpringBeanManager().getIpAddressManager().getProvince(ip));
        loginLog.setArea(SKBeanUtils.getLocalSpringBeanManager().getIpAddressManager().getSubdivision(ip));
        loginLog.setNickName(example.getNickname());
        loginLog.setDayTime(DateUtil.currentTimeSeconds());
        loginLog.setLogtype(logtype);
        loginLog.setTelephone(example.getTelephone());
        loginLog.setOperatingSystem(DeviceUtils.isMobileDevice(request).get("deviceName"));

//        RLock lock = SKBeanUtils.getRedisService().getLock(String.format(String.valueOf(example.getUserId()),logtype));
        SKBeanUtils.getRedisService().savegetUserOperatioIp(example.getUserId(),loginLog);
        getDatastore().save(loginLog);
//        try{
//            getDatastore().save(loginLog);
//            SKBeanUtils.getRedisService().savegetUserOperatioIp(example.getUserId(),loginLog);
//            boolean res = lock.tryLock(3, 5, TimeUnit.SECONDS);
//            if(res){ //成功
//                SKBeanUtils.getRedisService().savegetUserOperatioIp(example.getUserId(),loginLog);
//                getDatastore().save(loginLog);
//            }
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        } finally {
//            lock.unlock();
//        }
    }

}
