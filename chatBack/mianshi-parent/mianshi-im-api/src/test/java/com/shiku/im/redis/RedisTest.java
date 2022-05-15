package com.shiku.im.redis;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RBucket;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.shiku.mianshi.Application;

import cn.xyz.commons.constants.KConstants;
import cn.xyz.mianshi.utils.SKBeanUtils;
import cn.xyz.mianshi.vo.Friends;
import cn.xyz.mianshi.vo.Room;


@RunWith(SpringRunner.class)
@SpringBootTest(classes=Application.class)
public class RedisTest {

	private static final Logger log = LoggerFactory.getLogger(RedisTest.class);
	
	@Autowired(required=false)
    private RedissonClient redissonClient;
	
	@Test
	public void testRoom() {
		Room room=new Room();
		ObjectId roomId=new ObjectId();
		room.setId(roomId);
		room.setName("测试1111");
		room.setUserId(1000010);
		String objStr=JSONObject.toJSONString(room);
		log.info("new ====> "+objStr);
		log.info("new id  ====> "+roomId.toString());
		RBucket<Room> bucket = redissonClient.getBucket("rooms:"+roomId.toString());
		bucket.set(room,KConstants.Expire.DAY1,TimeUnit.SECONDS);
		
		
		RBucket<Room> bucket1 = redissonClient.getBucket("rooms:"+roomId.toString());
		Room object = bucket1.get();
		log.info("end ====> "+JSONObject.toJSONString(object));
		log.info("end id  ====> "+object.getId().toString());
		redissonClient.shutdown();
	}
	
	@Test
	public void testListFriends() {
		List<Friends> list=new ArrayList<>();
		Friends friends=new Friends();
		friends.setId(new ObjectId());
		friends.setNickname("name1");
		friends.setToNickname("name3");
		friends.setUserId(10000);
		friends.setToUserId(1200);
		list.add(friends);
		Friends friends1=new Friends();
		friends1.setId(new ObjectId());
		friends1.setNickname("name12");
		friends1.setToNickname("name4");
		friends1.setUserId(100000);
		friends1.setToUserId(12000);
		list.add(friends1);
		String objStr=JSONObject.toJSONString(list);
		log.info("new ====> "+objStr);
		log.info("new id  ====> "+friends.getId().toString());
		RList<Friends> bucket = redissonClient.getList("rooms:"+1000);
		bucket.clear();
		//bucket.set(list,KConstants.Expire.DAY1,TimeUnit.SECONDS);
		bucket.addAll(list);
		
		RList<Friends> list2 = redissonClient.getList("rooms:"+1000);
		
		log.info("end ====> "+JSONObject.toJSONString(list2));
		log.info("end id  ====> "+list2.get(0).getId().toString());
		redissonClient.shutdown();
	}
	
	@Test
	public void testFastJsonRoom() {
		Room room=new Room();
		ObjectId roomId=new ObjectId();
		room.setId(roomId);
		room.setName("测试1111");
		room.setUserId(1000010);
		String objStr=JSONObject.toJSONString(room);
		log.info("new ====> "+objStr);
		log.info("new id  ====> "+roomId.toString());
		RBucket<String> bucket = redissonClient.getBucket("rooms:"+roomId.toString());
		bucket.set(objStr,KConstants.Expire.DAY1,TimeUnit.SECONDS);
		
		
		RBucket<String> bucket1 = redissonClient.getBucket("rooms:"+roomId.toString());
		String object = bucket1.get();
		Room room2=JSON.parseObject(object, Room.class);
		log.info("end ====> "+JSONObject.toJSONString(room2));
		log.info("end id  ====> "+room2.getId().toString());
		redissonClient.shutdown();
	}
	
	@Test
	public void testUserJids() {
		System.out.println(SKBeanUtils.getRedisService().queryRoomPushMemberUserIds("1111"));
		redissonClient.shutdown();
	}
	
	@Test
	public void testTTL() {
		//,StringCodec.INSTANCE
		RBucket<Object> bucket = redissonClient.getBucket("ttl");
		bucket.set("123456", 600, TimeUnit.SECONDS);
		try {
			Thread.sleep(2000);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("ttl "+bucket.remainTimeToLive()/1000);
		redissonClient.shutdown();
	}
}
