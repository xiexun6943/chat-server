package com.shiku.im.xmpp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jivesoftware.smack.XMPPConnection;

import cn.xyz.commons.support.Callback;
import cn.xyz.commons.utils.ThreadUtil;

/** @version:（1.0） 
* @ClassName	PressureTest
* @Description: （压力测试） 
* @date:2018年11月13日下午5:46:54  
*/ 

public class PressureTest extends SmackBaseTest{
	
	
	
	@Override
	public void initXmpp() {
		xmppHost="192.168.0.168";
		domain="im.server.com";
	}
	/**
	 *	1. 群内生成1000个用户 
	 *  
	 *  2. 模拟四百个用户发送消息 
	 *  
	 *  3. 可以 发送总条数、每秒条数 
	 */
	
	
	
	
	/**
	 *  /console/sendMsg
	 * 
	 */
	
	
	
	public void mucTest(){
		List<String> roomJidList=Collections.synchronizedList(new ArrayList<>());
		roomJidList.add("5bea99d93c4c0c31b03f0b81");
		int userCount=10;
		List<XMPPConnection> connList=Collections.synchronizedList(new ArrayList<>());
		XMPPConnection conn=null;
		for (int i = 0; i < userCount; i++) {
			//XMPPConnection conn=getConnection(username, password)
		}
		for (String jid : roomJidList) {
			ThreadUtil.executeInThread(new Callback() {
				@Override
				public void execute(Object obj) {
					mucSendMsg(jid);
				}
			});
		}
	}
	
	public void mucSendMsg(final String jid) {
		
	}
	
	
}
