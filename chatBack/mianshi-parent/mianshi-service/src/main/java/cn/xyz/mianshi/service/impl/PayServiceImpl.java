package cn.xyz.mianshi.service.impl;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.Query;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONObject;
import com.wxpay.utils.GetWxOrderno;
import com.wxpay.utils.MD5Util;
import com.wxpay.utils.RequestHandler;
import com.wxpay.utils.WXPayUtil;
import com.wxpay.utils.http.HttpClientConnectionManager;

import cn.xyz.commons.constants.KConstants;
import cn.xyz.commons.ex.ServiceException;
import cn.xyz.commons.support.Callback;
import cn.xyz.commons.utils.DateUtil;
import cn.xyz.commons.utils.StringUtil;
import cn.xyz.commons.utils.ThreadUtil;
import cn.xyz.commons.vo.JSONMessage;
import cn.xyz.mianshi.opensdk.entity.SkOpenApp;
import cn.xyz.mianshi.scheduleds.TimerTask;
import cn.xyz.mianshi.service.PayService;
import cn.xyz.mianshi.utils.SKBeanUtils;
import cn.xyz.mianshi.vo.CodePay;
import cn.xyz.mianshi.vo.ConsumeRecord;
import cn.xyz.mianshi.vo.PayOrder;
import cn.xyz.mianshi.vo.User;
import cn.xyz.service.AuthServiceUtils;
import cn.xyz.service.KXMPPServiceImpl;
import cn.xyz.service.KXMPPServiceImpl.MessageBean;
import cn.xyz.service.RedisServiceImpl;
@Service
public class PayServiceImpl extends MongoRepository<CodePay,ObjectId> implements PayService {
	
	public static DefaultHttpClient httpclient = new DefaultHttpClient(new ThreadSafeClientConnManager());
	
	@Override
	public Datastore getDatastore() {
		// TODO Auto-generated method stub
		return SKBeanUtils.getDatastore();
	}

	@Override
	public Class<CodePay> getEntityClass() {
		// TODO Auto-generated method stub
		return CodePay.class;
	}
	
	@SuppressWarnings("unused")
	private static RedisServiceImpl getRedisServiceImpl(){
		return SKBeanUtils.getRedisService();
	}
	
	/**
	 * ??????20????????????
	 * ????????????   (userId+n+opt)??????+(userId+n+opt)+opt+(time/opt)
	 * @param paymentCode
	 * @return
	 */
	public Integer analysisCode(String paymentCode){
		int n=9;// ?????????
		String userIdCodeLength=paymentCode.substring(0, 1);// ???????????????userId+n+opt????????????
		
		// userIdCode=userId+n+opt
		String userIdCode=paymentCode.substring(userIdCodeLength.length(),Integer.valueOf(userIdCodeLength)+1);
		
		int three=userIdCodeLength.length()+userIdCode.length();
		String opt=paymentCode.substring(three, three+3);
		
		int four=three+3;
		// timeCode=time/opt
		String timeCode=paymentCode.substring(four,paymentCode.length());
		
		int userId=Integer.valueOf(userIdCode)-n-Integer.valueOf(opt);
		
		long time=Integer.valueOf(timeCode)*Integer.valueOf(opt);
		if(System.currentTimeMillis()/1000-time<256){
			return userId;
		}else{
			time=Integer.valueOf(timeCode)*(Integer.valueOf(opt)-100);
			if(System.currentTimeMillis()/1000-time<256){
				return userId;
			}else{
				return null;
			}
		}
		
	}
	
	/**
	 * ???????????????????????????
	 * @param userId ?????????
	 * @param fromUserId ?????????--???????????????
	 * @param money
	 */
	public synchronized void paymentCodePay(String paymentCode,Integer userId,Integer fromUserId,String money,String desc){
		User fromUser=SKBeanUtils.getUserManager().get(fromUserId);
		User user=SKBeanUtils.getUserManager().get(userId);
		if(fromUser.getBalance()<Double.valueOf(money)){
			throw new ServiceException("??????????????????,????????????");
		}
		CodePay codePay=new CodePay();
		codePay.setUserId(fromUserId);
		codePay.setUserName(fromUser.getNickname());
		codePay.setType(1);
		codePay.setToUserId(userId);
		codePay.setToUserName(user.getNickname());
		codePay.setMoney(Double.valueOf(money));
		codePay.setCreateTime(DateUtil.currentTimeSeconds());
		saveCodePay(codePay);
		// ??????
		SKBeanUtils.getUserManager().rechargeUserMoeny(fromUserId, Double.valueOf(money), KConstants.MOENY_REDUCE);
		String lessTradeNo=StringUtil.getOutTradeNo();
		//????????????????????????
		ConsumeRecord lessRecord=new ConsumeRecord();
		lessRecord.setUserId(fromUserId);
		lessRecord.setToUserId(userId);
		lessRecord.setTradeNo(lessTradeNo);
		lessRecord.setMoney(Double.valueOf(money));
		lessRecord.setStatus(KConstants.OrderStatus.END);
		lessRecord.setType(KConstants.ConsumeType.SEND_PAYMENTCODE);
		lessRecord.setPayType(KConstants.PayType.BALANCEAY); //????????????
		if(!StringUtil.isEmpty(desc))
			lessRecord.setDesc(desc);
		else
			lessRecord.setDesc("??????????????????");
		lessRecord.setTime(DateUtil.currentTimeSeconds());
		SKBeanUtils.getConsumeRecordManager().save(lessRecord);
		
		// ??????xmpp??????????????????
		User sysUser=SKBeanUtils.getUserManager().get(1100);
		MessageBean messageBean = new MessageBean();
		messageBean.setFromUserId(sysUser.getUserId().toString());
		messageBean.setFromUserName(sysUser.getNickname());
		messageBean.setType(KXMPPServiceImpl.CODEPAYMENT);
		messageBean.setContent(JSONObject.toJSONString(codePay));
		messageBean.setMsgType(0);// ??????????????????
		messageBean.setMessageId(StringUtil.randomUUID());
		messageBean.setToUserId(fromUserId.toString());
		messageBean.setToUserName(fromUser.getNickname());
		try {
			KXMPPServiceImpl.getInstance().send(messageBean);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// ??????
		SKBeanUtils.getUserManager().rechargeUserMoeny(userId, Double.valueOf(money), KConstants.MOENY_ADD);
		String addTradeNo=StringUtil.getOutTradeNo();
		//????????????????????????
		ConsumeRecord addRecord=new ConsumeRecord();
		addRecord.setUserId(userId);
		addRecord.setUserId(fromUserId);
		addRecord.setTradeNo(addTradeNo);
		addRecord.setMoney(Double.valueOf(money));
		addRecord.setStatus(KConstants.OrderStatus.END);
		addRecord.setType(KConstants.ConsumeType.RECEIVE_PAYMENTCODE);
		addRecord.setPayType(KConstants.PayType.BALANCEAY); //????????????
		addRecord.setDesc("??????????????????");
		addRecord.setTime(DateUtil.currentTimeSeconds());
		SKBeanUtils.getConsumeRecordManager().save(addRecord);
		
		// ??????xmpp??????????????????
		MessageBean message=new MessageBean();
		message.setFromUserId(sysUser.getUserId().toString());
		message.setFileName(sysUser.getNickname());
		message.setType(KXMPPServiceImpl.CODEARRIVAL);
		message.setMsgType(0);
		message.setMessageId(StringUtil.randomUUID());
		message.setContent(JSONObject.toJSONString(codePay));
		message.setToUserId(userId.toString());
		message.setToUserName(user.getNickname());
		try {
			KXMPPServiceImpl.getInstance().send(message);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// ???????????????????????????
		getRedisServiceImpl().savePaymentCode(paymentCode, fromUserId);
	}
	
	/**
	 * ????????????????????????
	 * @param userId
	 * @param paymentCode
	 * @return
	 */
	public boolean checkPaymentCode(Integer userId,String paymentCode){
		Integer value=getRedisServiceImpl().getPaymentCode(paymentCode);
		if(null!=value){
			return true;
		}else{
			return false;
		}
		
	}
	
	/**
	 * ???????????????????????????
	 * @param userId  ?????????(????????????)
	 * @param fromUserId  ?????????(????????????)--???????????????
	 * @param money
	 */
	public synchronized void receipt(Integer userId,Integer fromUserId,String money,String desc){
		User user = SKBeanUtils.getUserManager().get(userId);
		User fromUser = SKBeanUtils.getUserManager().get(fromUserId);
		if(user.getBalance()<Double.valueOf(money)){
			throw new ServiceException("???????????????????????????");
		}
		
		CodePay codePay=new CodePay();
		codePay.setUserId(fromUserId);
		codePay.setType(2);
		codePay.setUserName(fromUser.getNickname());
		codePay.setToUserId(userId);
		codePay.setToUserName(user.getNickname());
		codePay.setMoney(Double.valueOf(money));
		codePay.setCreateTime(DateUtil.currentTimeSeconds());
		saveCodePay(codePay);
		
		SKBeanUtils.getUserManager().rechargeUserMoeny(userId, Double.valueOf(money), KConstants.MOENY_REDUCE);
		String lessTradeNo=StringUtil.getOutTradeNo();
		//???????????????????????? 
		ConsumeRecord lessRecord=new ConsumeRecord();
		lessRecord.setUserId(userId);
		lessRecord.setToUserId(fromUserId);
		lessRecord.setTradeNo(lessTradeNo);
		lessRecord.setMoney(Double.valueOf(money));
		lessRecord.setStatus(KConstants.OrderStatus.END);
		lessRecord.setType(KConstants.ConsumeType.SEND_QRCODE);
		lessRecord.setPayType(KConstants.PayType.BALANCEAY); // ????????????
		if(!StringUtil.isEmpty(desc))
			lessRecord.setDesc(desc);
		else
			lessRecord.setDesc("????????????????????????");
		lessRecord.setTime(DateUtil.currentTimeSeconds());
		SKBeanUtils.getConsumeRecordManager().save(lessRecord);
		
		User sysUser=SKBeanUtils.getUserManager().get(1100);
		MessageBean messageBean = new MessageBean();
		messageBean.setFromUserId(sysUser.getUserId().toString());
		messageBean.setFromUserName(sysUser.getNickname());
		messageBean.setType(KXMPPServiceImpl.CODERECEIPT);
		messageBean.setContent(JSONObject.toJSONString(codePay));
		messageBean.setMsgType(0);// ??????????????????
		messageBean.setMessageId(StringUtil.randomUUID());
		messageBean.setToUserId(user.getUserId().toString());
		messageBean.setToUserName(user.getNickname());
		try {
			KXMPPServiceImpl.getInstance().send(messageBean);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// ??????
		SKBeanUtils.getUserManager().rechargeUserMoeny(fromUserId, Double.valueOf(money), KConstants.MOENY_ADD);
		String addTradeNo=StringUtil.getOutTradeNo();
		// ????????????????????????
		ConsumeRecord addRecord=new ConsumeRecord();
		addRecord.setUserId(fromUserId);
		addRecord.setToUserId(userId);
		addRecord.setTradeNo(addTradeNo);
		addRecord.setMoney(Double.valueOf(money));
		addRecord.setStatus(KConstants.OrderStatus.END);
		addRecord.setType(KConstants.ConsumeType.RECEIVE_QRCODE);
		addRecord.setPayType(KConstants.PayType.BALANCEAY); //????????????
		addRecord.setDesc("????????????????????????");
		addRecord.setTime(DateUtil.currentTimeSeconds());
		SKBeanUtils.getConsumeRecordManager().save(addRecord);
		
		// ??????xmpp??????????????????
		MessageBean message=new MessageBean();
		message.setFromUserId(sysUser.getUserId().toString());
		message.setFromUserName(sysUser.getNickname());
		message.setType(KXMPPServiceImpl.CODEERECEIPTARRIVAL);
		message.setMsgType(0);
		message.setMessageId(StringUtil.randomUUID());
		message.setContent(JSONObject.toJSON(codePay));
		message.setToUserId(fromUser.getUserId().toString());
		message.setToUserName(fromUser.getNickname());
		try {
			KXMPPServiceImpl.getInstance().send(message);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void saveCodePay(CodePay entity){
		save(entity);
		
	}
	
	/**
	 * ??????????????????
	 * @param xml
	 * @return
	 * @throws Exception
	 */
	public JSONMessage unifiedOrderImpl(Map<String, String> map) throws Exception{
		// ??????
		if(!checkSign(map.get("appId"), map.get("body"), map.get("input_charset"), map.get("nonce_str"), map.get("out_trade_no"), map.get("total_fee"), map.get("trade_type"),map.get("notify_url"),map.get("spbill_create_ip"), map.get("sign"))){
			return JSONMessage.failure("????????????");
		}
		
		// ??????app???????????????????????????
		if(map.get("trade_type").equals("APP")){
			SkOpenApp openApp = SKBeanUtils.getOpenAppManage().getOpenAppByAppId(map.get("appId"));
			if(null==openApp){
				return JSONMessage.failure("?????????????????????????????????");
			}else if(openApp.getIsAuthPay()!=1){
				return JSONMessage.failure("??????????????????");
			}
		}else if(map.get("trade_type").equals("WEB")){
			SkOpenApp openWeb=SKBeanUtils.getOpenWebAppManage().checkWebAPPByAppId(map.get("appId"));
			if(null==openWeb){
				return JSONMessage.failure("?????????????????????????????????");
			}else if(openWeb.getIsAuthPay()!=1){
				return JSONMessage.failure("??????????????????");
			}
		}
		
		// ????????????
		PayOrder payOrder = new PayOrder();
		payOrder.setAppId(map.get("appId"));
		payOrder.setMoney(map.get("total_fee"));
		payOrder.setTrade_no(map.get("trade_no"));
		payOrder.setCreateTime(DateUtil.currentTimeSeconds());
		payOrder.setIPAdress(map.get("spbill_create_ip"));
		payOrder.setDesc(map.get("body"));
		payOrder.setAppType(map.get("trade_type"));
		payOrder.setCallBackUrl(map.get("notify_url"));
		payOrder.setStatus((byte)0);
		payOrder.setSign(map.get("sign"));
		payOrder.setId(ObjectId.get());
		saveEntity(payOrder);
		
		SKBeanUtils.getRedisService().savePayOrderSign(payOrder.getId().toString(), map.get("sign"));
		
		SortedMap<String, String> contentMap = new TreeMap<String, String>();
		contentMap.put("prepay_id", payOrder.getId().toString());
		String resultxml = WXPayUtil.paramsToxmlStr(contentMap);
		return JSONMessage.success(resultxml);
	}
	
	/**
	 * ????????????
	 * @param appId 
	 * @param body ????????????
	 * @param input_charset ????????????
	 * @param nonce_str ????????????
	 * @param trade_no ????????????
	 * @param total_fee ??????
	 * @param trade_type ??????????????????
	 * @param sign ??????
	 * @return
	 */
	public boolean checkSign(String appId,String body,String input_charset,String nonce_str,String trade_no,
			String total_fee,String trade_type,String notify_url,String spbill_create_ip,String sign){
		SortedMap<String, String> contentMap = new TreeMap<String, String>();
		contentMap.put("appId", appId);
		contentMap.put("body", body);
		contentMap.put("input_charset", input_charset);
		contentMap.put("nonce_str", nonce_str);
		contentMap.put("notify_url", notify_url);
		contentMap.put("spbill_create_ip", spbill_create_ip);
		contentMap.put("total_fee", total_fee);
		contentMap.put("out_trade_no", trade_no);
		contentMap.put("trade_type", trade_type);
		
		String signKey=createSign(contentMap);
		
		if(signKey.equals(sign)){
			return true;
		}else{
			return false;
		}
		
	}
	
	/**
	 * ???????????????????????????
	 * @param appId
	 * @param prepayId
	 * @return
	 */
	public JSONMessage getOrderInfo(String appId,String prepayId){
		Query<PayOrder> query = getDatastore().createQuery(PayOrder.class).field("_id").equal(new ObjectId(prepayId));
		query.field("appId").equal(appId);
		PayOrder payOrder = query.get();
		Map<String,String> map = new HashMap<>();
		map.put("money", payOrder.getMoney());
		map.put("desc", payOrder.getDesc());
		return JSONMessage.success(null, map);
	}
	
	/**
	 * ??????????????????
	 * @param appId
	 * @param prepayId
	 * @param sign
	 * @param userId
	 * @return
	 */
	public JSONMessage passwordPayment(String appId,String prepayId,String sign,Integer userId,String token,long time,String secret){
		
		User user = SKBeanUtils.getUserManager().get(userId);
		if(!AuthServiceUtils.authPaymentSecret(userId.toString(), token, user.getPayPassword(), time, secret)){
			return JSONMessage.failure("??????????????????");
		}
		
		if(!SKBeanUtils.getRedisService().queryPayOrderSign(prepayId).equals(sign)){
			return JSONMessage.failure("????????????");
		}
		
		Query<PayOrder> query = getDatastore().createQuery(PayOrder.class).field("_id").equal(new ObjectId(prepayId));
		if(null==query.get()){
			return JSONMessage.failure("???????????????");
		}
		
		PayOrder order = query.get();
		if(!order.getAppId().equals(appId)){
			return JSONMessage.failure("appId??????");
		}
		order.setUserId(userId.toString());
		order.setStatus((byte)1);
		saveEntity(order);
		
		// ??????????????????
		String lessTradeNo=StringUtil.getOutTradeNo();
		ConsumeRecord consumeRecord = new ConsumeRecord();
		consumeRecord.setMoney(Double.valueOf(order.getMoney()));
		consumeRecord.setOrderId(order.getId());
		// ????????????type????????????
		consumeRecord.setType(14);
		consumeRecord.setPayType(3);
		consumeRecord.setDesc(order.getDesc());
		consumeRecord.setTime(DateUtil.currentTimeSeconds());
		consumeRecord.setStatus(KConstants.OrderStatus.END);
		consumeRecord.setTradeNo(lessTradeNo);
		consumeRecord.setUserId(userId);
		SKBeanUtils.getConsumeRecordManager().save(consumeRecord);
		
		// ??????
		SKBeanUtils.getUserManager().rechargeUserMoeny(userId, Double.valueOf(order.getMoney()), KConstants.MOENY_REDUCE);
		
		User sysUser=SKBeanUtils.getUserManager().get(1100);
		SkOpenApp openApp = SKBeanUtils.getOpenAppManage().getOpenAppByAppId(appId);
		JSONObject obj = new JSONObject();
		obj.put("orderId", order.getId().toString());
		obj.put("money", order.getMoney());
		obj.put("icon", openApp.getAppImg());
		obj.put("name", openApp.getAppName());
		MessageBean message=new MessageBean();
		message.setFromUserId(sysUser.getUserId().toString());
		message.setFromUserName(sysUser.getNickname());
		message.setType(KXMPPServiceImpl.OPENPAYSUCCESS);
		message.setMsgType(0);
		message.setMessageId(StringUtil.randomUUID());
		message.setContent(obj);
		message.setToUserId(user.getUserId().toString());
		message.setToUserName(user.getNickname());
		try {
			KXMPPServiceImpl.getInstance().send(message);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// ????????????????????????
		ThreadUtil.executeTimerTask(new PaySyncCallback(order),5,30);
		
		return JSONMessage.success();
	}
	/**
	 * ??????????????????
	 * @param appId
	 * @param prepayId
	 * @param sign
	 * @param userId
	 * @return
	 */
	public JSONMessage passwordPaymentV1(String appId,String prepayId,String sign,Integer userId){
		
		User user = SKBeanUtils.getUserManager().get(userId);
		if(!SKBeanUtils.getRedisService().queryPayOrderSign(prepayId).equals(sign)){
			return JSONMessage.failure("????????????");
		}
		
		Query<PayOrder> query = getDatastore().createQuery(PayOrder.class).field("_id").equal(new ObjectId(prepayId));
		if(null==query.get()){
			return JSONMessage.failure("???????????????");
		}
		
		PayOrder order = query.get();
		if(!order.getAppId().equals(appId)){
			return JSONMessage.failure("appId??????");
		}
		order.setUserId(userId.toString());
		order.setStatus((byte)1);
		saveEntity(order);
		
		// ??????????????????
		String lessTradeNo=StringUtil.getOutTradeNo();
		ConsumeRecord consumeRecord = new ConsumeRecord();
		consumeRecord.setMoney(Double.valueOf(order.getMoney()));
		consumeRecord.setOrderId(order.getId());
		// ????????????type????????????
		consumeRecord.setType(14);
		consumeRecord.setPayType(3);
		consumeRecord.setDesc(order.getDesc());
		consumeRecord.setTime(DateUtil.currentTimeSeconds());
		consumeRecord.setStatus(KConstants.OrderStatus.END);
		consumeRecord.setTradeNo(lessTradeNo);
		consumeRecord.setUserId(userId);
		SKBeanUtils.getConsumeRecordManager().save(consumeRecord);
		
		// ??????
		SKBeanUtils.getUserManager().rechargeUserMoeny(userId, Double.valueOf(order.getMoney()), KConstants.MOENY_REDUCE);
		
		User sysUser=SKBeanUtils.getUserManager().get(1100);
		SkOpenApp openApp = SKBeanUtils.getOpenAppManage().getOpenAppByAppId(appId);
		JSONObject obj = new JSONObject();
		obj.put("orderId", order.getId().toString());
		obj.put("money", order.getMoney());
		obj.put("icon", openApp.getAppImg());
		obj.put("name", openApp.getAppName());
		MessageBean message=new MessageBean();
		message.setFromUserId(sysUser.getUserId().toString());
		message.setFromUserName(sysUser.getNickname());
		message.setType(KXMPPServiceImpl.OPENPAYSUCCESS);
		message.setMsgType(0);
		message.setMessageId(StringUtil.randomUUID());
		message.setContent(obj);
		message.setToUserId(user.getUserId().toString());
		message.setToUserName(user.getNickname());
		try {
			KXMPPServiceImpl.getInstance().send(message);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// ????????????????????????
		ThreadUtil.executeTimerTask(new PaySyncCallback(order),5,30);
		
		return JSONMessage.success();
	}
	
	
	/**
	 * ??????md5??????,?????????:???????????????a-z??????,???????????????????????????????????????
	 */
	public String createSign(SortedMap<String, String> packageParams) {
		StringBuffer sb = new StringBuffer();
		Set es = packageParams.entrySet();
		Iterator it = es.iterator();
		while (it.hasNext()) {
			Map.Entry entry = (Map.Entry) it.next();
			String k = (String) entry.getKey();
			String v = (String) entry.getValue();
			if (null != v && !"".equals(v) && !"sign".equals(k)
					&& !"key".equals(k)) {
				sb.append(k + "=" + v + "&");
				//System.out.println(k+"----"+v);
			}
		}
//		sb.append("key=" + this.getKey());
		//System.out.println("key====="+this.getKey());
		String sign = MD5Util.MD5Encode(sb.toString(), "UTF-8")
				.toUpperCase();
		return sign;

	}
	
	private class PaySyncCallback extends TimerTask{
		private PayOrder order;
		/**
		 * 
		 */
		public PaySyncCallback(PayOrder order) {
			this.order=order;
		}
		@Override
		public void run() {
			String callBackUrl = order.getCallBackUrl();
			if(StringUtil.isEmpty(callBackUrl)) {
				if(order.getAppType().equals("APP")){
					Query<SkOpenApp> openAppQuery=getDatastore().createQuery(SkOpenApp.class).field("appId").equal(order.getAppId());
					callBackUrl = openAppQuery.get().getPayCallBackUrl();
				}else{
					Query<SkOpenApp> openWebQuery = getDatastore().createQuery(SkOpenApp.class).field("appId").equal(order.getAppId());
					callBackUrl = openWebQuery.get().getPayCallBackUrl();
				}
			}
			try {
				SortedMap<String, String> contentMap = new TreeMap<String, String>();
				contentMap.put("out_trade_no", order.getTrade_no());
				String xmlParam = WXPayUtil.paramsToxmlStr(contentMap);
				HttpPost httpPost = HttpClientConnectionManager.getPostMethod(callBackUrl);
				httpPost.setEntity(new StringEntity(xmlParam,"UTF-8"));
				logger.info("?????????????????????,????????????");
				HttpResponse response =httpclient.execute(httpPost);
				int responseCode = response.getStatusLine().getStatusCode();
				if(responseCode==200){
					logger.info("??????????????????");
					this.cancel();
				}else{
					logger.info("????????????????????????????????????");
				}
			}catch(UnknownHostException e1){
				logger.error(e1.getMessage()+" ??????????????????????????????");
			}catch (Exception e) {
				e.printStackTrace();
				this.cancel();
			}
		}
			
		
		
	}
}
