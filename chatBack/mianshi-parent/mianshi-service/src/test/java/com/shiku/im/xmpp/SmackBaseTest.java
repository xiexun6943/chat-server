package com.shiku.im.xmpp;

import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.stringprep.XmppStringprepException;

import cn.xyz.service.KXMPPServiceImpl.MyConnectionListener;

/**
* @Description: TODO(用一句话描述该文件做什么)
* @author lidaye
* @date 2018年8月22日 
*/

public  class SmackBaseTest  {
	
	
	public static String xmppHost="192.168.0.168";
	public static String domain="lidaye";
	
	protected XMPPTCPConnection conn=null;
	protected String username=null;
	protected String password=null;
	
	public  void initXmpp() {};
	private XMPPTCPConnectionConfiguration getConfig(){
	XMPPTCPConnectionConfiguration config=null;
		if (null == config) {
			try {
				config= XMPPTCPConnectionConfiguration.builder()
					//.setUsernameAndPassword("admin", "admin")
					.setSecurityMode(SecurityMode.ifpossible)
					.setSendPresence(true)
					.setXmppDomain(domain)
				   .setHost(xmppHost).setPort(5222)
				    .setCompressionEnabled(false)
					.setResource("Smack")
					.build();
				
			} catch (XmppStringprepException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
				
		}
		return config;
	}
	public XMPPTCPConnection getConnection(String username, String password) {
		XMPPTCPConnection connection=null;
		try {
			
			connection = new XMPPTCPConnection(getConfig());
			connection.connect();
			connection.login(username, password);
			connection.addConnectionListener(new MyConnectionListener(connection,true));
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return connection;
	}
	


	
	
	
	
	
	

}

