package cn.xyz.mianshi.opensdk;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.springframework.stereotype.Service;

import cn.xyz.commons.ex.ServiceException;
import cn.xyz.commons.utils.DateUtil;
import cn.xyz.commons.utils.StringUtil;
import cn.xyz.mianshi.model.PageResult;
import cn.xyz.mianshi.opensdk.entity.SkOpenAccount;
import cn.xyz.mianshi.service.impl.MongoRepository;
import cn.xyz.mianshi.utils.SKBeanUtils;
import cn.xyz.mianshi.vo.User;
@Service(value="openAccountManage")
public class OpenAccountManageImpl extends MongoRepository<SkOpenAccount,ObjectId> {

	@Override
	public Datastore getDatastore() {
		return SKBeanUtils.getDatastore();
	}

	@Override
	public Class<SkOpenAccount> getEntityClass() {
		return SkOpenAccount.class;
	}
	/*public Object loginAccount(String account,String password) {
		Object result=null;
		if(account.contains("@")) {
			
			
		}else {
			result=loginUserAccount(account, password);
		}
	}*/
	
	
	
	public SkOpenAccount loginUserAccount(String telephone,String password,HttpServletRequest request,HttpServletResponse response) {
		SkOpenAccount account=null;
		User user = null;
		try {
			account=queryOne("telephone", telephone);
			if(null==account){
				user=SKBeanUtils.getUserManager().login(telephone, password);
				request.getSession().setAttribute("openadmin", user);
				account = queryOne("userId", user.getUserId());
				 account=new SkOpenAccount();
				 account.setUserId(user.getUserId());
				 account.setCreateTime(DateUtil.currentTimeSeconds());
				 account.setTelephone(user.getTelephone());
				 account.setPassword(user.getPassword());
				 account.setId(new ObjectId());
				 save(account);
			}
			if(null!=account.getStatus()){
				if(account.getStatus()==-1)
					throw new ServiceException("?????????????????????");
			}
			
			if(account.getPassword().equals(password)){
				return account;
			}else{
				throw new ServiceException("?????????????????????");
			}
		} catch (ServiceException e) {
			throw e; 
		}catch (Exception e) {
			e.printStackTrace();
			return account;
		}
		
		
	}
	
	/**
	 * ??????????????????
	 * @param skOpenAccount
	 */
	public void perfectUserInfo(SkOpenAccount skOpenAccount){
		Query<SkOpenAccount> query=getDatastore().createQuery(SkOpenAccount.class).field("userId").equal(skOpenAccount.getUserId());
		UpdateOperations<SkOpenAccount> ops=getDatastore().createUpdateOperations(SkOpenAccount.class);
		if(!StringUtil.isEmpty(skOpenAccount.getMail()))
			ops.set("mail", skOpenAccount.getMail());
		if(!StringUtil.isEmpty(skOpenAccount.getIdCard()))
			ops.set("idCard", skOpenAccount.getIdCard());
		if(!StringUtil.isEmpty(skOpenAccount.getTelephone()))
			ops.set("telephone", skOpenAccount.getTelephone());
		if(!StringUtil.isEmpty(skOpenAccount.getAddress()))
			ops.set("address", skOpenAccount.getAddress());
		if(!StringUtil.isEmpty(skOpenAccount.getRealName()))
			ops.set("realName", skOpenAccount.getRealName());
		if(!StringUtil.isEmpty(skOpenAccount.getCompanyName()))
			ops.set("companyName", skOpenAccount.getCompanyName());
		if(!StringUtil.isEmpty(skOpenAccount.getBusinessLicense()))
			ops.set("businessLicense", skOpenAccount.getBusinessLicense());
		
		getDatastore().update(query, ops);
	}
	
	/**
	 * ????????????
	 * @param userId
	 * @param oldPassword
	 * @param newPassword
	 * @throws Exception 
	 */
	public void updatePassword(Integer userId,String oldPassword,String newPassword) throws Exception{
		Query<SkOpenAccount> query=getDatastore().createQuery(SkOpenAccount.class).field("userId").equal(userId);
		UpdateOperations<SkOpenAccount> ops=getDatastore().createUpdateOperations(SkOpenAccount.class);
		if(query.get().getPassword().equals(oldPassword)){
			ops.set("password",newPassword);
		}else{
			throw new Exception("???????????????");
		}
		getDatastore().update(query, ops);
	}
	
	/**
	 * ??????????????????
	 * @param telephone
	 * @param password
	 * @return
	 */
	public SkOpenAccount ckeckOpenAccount(String telephone,String password){
		Query<SkOpenAccount> query=getDatastore().createQuery(SkOpenAccount.class).field("telephone").equal(telephone).field("password").equal(password);
		return query.get();
	}
	
	/**
	 * ??????????????????
	 * @param userId
	 * @return
	 */
	public SkOpenAccount getOpenAccount(Integer userId){
		Query<SkOpenAccount> query=getDatastore().createQuery(SkOpenAccount.class).field("userId").equal(userId);
		return query.get();
	}
	
	/**
	 * ?????????????????????
	 * @param userId
	 * @param status
	 */
	public void applyDeveloper(Integer userId,int status){
		Query<SkOpenAccount> query=getDatastore().createQuery(SkOpenAccount.class).field("userId").equal(userId);
		UpdateOperations<SkOpenAccount> ops=getDatastore().createUpdateOperations(SkOpenAccount.class);
		ops.set("status", status);
		ops.set("modifyTime", DateUtil.currentTimeSeconds());
		getDatastore().update(query, ops);
	}
	
	/**
	 * ???????????????
	 * @param pageIndex
	 * @param pageSize
	 * @param status
	 * @return
	 */
	public PageResult<SkOpenAccount> developerList(int pageIndex,int pageSize,int status,String keyWorld){
		Query<SkOpenAccount> query=getDatastore().createQuery(SkOpenAccount.class);
		if(status!=-2){
			query.field("status").equal(status);
		}
		if(!StringUtil.isEmpty(keyWorld)){
			query.or(query.criteria("userId").contains(keyWorld),query.criteria("telephone").contains(keyWorld));
		}
		PageResult<SkOpenAccount> data=new PageResult<>();
		data.setCount(query.count());
		data.setData(query.asList(pageFindOption(pageIndex, pageSize, 1)));
		return data;
	}
	
	public void deleteDeveloper(ObjectId id){
		Query<SkOpenAccount> query=getDatastore().createQuery(SkOpenAccount.class).field("_id").equal(id);
		getDatastore().delete(query);
	}
	
	/**
	 * ???????????????
	 * @param id
	 * @param userId
	 * @param status
	 */
	public void checkDeveloper(ObjectId id,int status){
		Query<SkOpenAccount> query=getDatastore().createQuery(SkOpenAccount.class).field("_id").equal(id);
		UpdateOperations<SkOpenAccount> ops=getDatastore().createUpdateOperations(SkOpenAccount.class);
		ops.set("status", status);
		ops.set("verifyTime", DateUtil.currentTimeSeconds());
		getDatastore().update(query, ops);
		
	}
}
