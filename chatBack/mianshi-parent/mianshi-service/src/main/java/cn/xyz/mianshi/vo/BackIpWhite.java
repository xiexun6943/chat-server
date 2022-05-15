package cn.xyz.mianshi.vo;

import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Indexed;

@Getter
@Setter
@Entity(value = "backIpWhite", noClassnameStored = true)
public class BackIpWhite {

    @Id
    private ObjectId id;

    @Indexed
    private String ip;

    private @Indexed long createTime;
}
