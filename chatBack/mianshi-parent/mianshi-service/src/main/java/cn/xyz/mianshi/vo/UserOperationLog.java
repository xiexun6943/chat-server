package cn.xyz.mianshi.vo;

import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Indexed;

import java.util.Date;

@Getter
@Setter
@Entity(value = "userOperationLog", noClassnameStored = true)
public class UserOperationLog {

    @Id
    private ObjectId id;
    private String nickName;
    @Indexed
    private String telephone;
    private @Indexed int userId; //用户Id
    @Indexed
    private String ip;
    private String area;
    private String browser;
    private String model;
    private String operatingSystem;
    @Indexed
    private Date actiontime;
    private Integer logtype;
    private String province;
    private @Indexed long dayTime;


}
