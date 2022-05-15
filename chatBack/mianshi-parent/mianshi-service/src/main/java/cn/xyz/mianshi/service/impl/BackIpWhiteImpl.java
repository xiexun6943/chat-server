package cn.xyz.mianshi.service.impl;

import cn.xyz.commons.utils.NetworkUtil;
import cn.xyz.mianshi.utils.SKBeanUtils;
import cn.xyz.mianshi.vo.BackIpWhite;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.query.Query;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@Service
public class BackIpWhiteImpl extends MongoRepository<BackIpWhite, ObjectId>{
    @Override
    public Datastore getDatastore() {
        return SKBeanUtils.getLocalSpringBeanManager().getDatastore();
    }

    @Override
    public Class<BackIpWhite> getEntityClass() {
        return BackIpWhite.class;
    }


    public boolean checkIpBlack(HttpServletRequest request){
        String ip= NetworkUtil.getIpAddress(request);
        Query<BackIpWhite> query = getDatastore().createQuery(getEntityClass()).field("ip").equal(ip);
        if(null==query.get()){
            return true;
        }
        return false;

    }
}
