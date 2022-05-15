package cn.xyz.mianshi.service.impl;

import cn.xyz.mianshi.utils.IPUtils;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import eu.bitwalker.useragentutils.Browser;
import eu.bitwalker.useragentutils.UserAgent;
import eu.bitwalker.useragentutils.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;

/**
 * ip地址服务
 */
@Service
public class IpAddressManagerImpl {

    private static Logger logger = LoggerFactory.getLogger(IpAddressManagerImpl.class);

    private static final String RE_TOP = "(?<![\\d.])192\\.168(\\.(2[0-4]\\d|25[0-5]|[01]?\\d{1,2})){2}(?![\\d.])";


    private static DatabaseReader reader;

    @Autowired
    private Environment env;

    @PostConstruct
    public void init() {
        try {
//            String dbPath=getClass().getClassLoader().getResource("areaDB/GeoLite2-City.mmdb").getPath();
//            String path = env.getProperty("geolite2.city.db.path");
//            if (StringUtils.isNotBlank(path)) {
//                dbPath = path;
//            }
////            System.err.println(dbPath);
//            File database = new File(dbPath);
//            System.err.println(database);
//            System.err.println(IPUtils.reader+">>>>>>>>>>>>>>>>");
            reader = IPUtils.reader;
        } catch (Exception e) {
            logger.error("IP地址服务初始化异常:" + e.getMessage(), e);
        }
    }

    /**
     * 获取ip地址所在区域
     * @param ipAddress
     * @return
     */
    public static String getSubdivision(String ipAddress){
        try {
            if(ipAddress.matches(RE_TOP)){
                return "内网";
            }
            CityResponse response = reader.city(InetAddress.getByName(ipAddress));
            String province = response.getMostSpecificSubdivision().getNames().get("zh-CN")
                    ==null?"":response.getMostSpecificSubdivision().getNames().get("zh-CN");
            String country=response.getCountry().getNames().get("zh-CN");
            String city=response.getCity().getNames().get("zh-CN")==null?"":response.getCity().getNames().get("zh-CN");
            return country+province+city;
        }catch (Exception e){
            logger.error("根据IP[{}]获取省份失败:{}", ipAddress, e.getMessage());
            return null;
        }
    }

    /**
     * 获取ip地址所在省份
     * @param ipAddress
     * @return
     */
    public static String getProvince(String ipAddress){
        try {
            if(ipAddress.matches(RE_TOP)){
                return "内网";
            }
            CityResponse response = reader.city(InetAddress.getByName(ipAddress));
            String province = response.getMostSpecificSubdivision().getNames().get("zh-CN");
            String country = response.getCountry().getNames().get("zh-CN");
            if(province==null){
                province = country;
                if(province == null){
                    province = "未知地区";
                }
            }
            return province;
        }catch (Exception e){
            logger.error("根据IP[{}]获取省份失败:{}", ipAddress, e.getMessage());
            return null;
        }
    }


    /**
     * 获取请求浏览器
     * @param request
     * @return
     */
    public static String getRequestBrowser(HttpServletRequest request){
        String info;
        try{
            Browser browser = UserAgent.parseUserAgentString(request.getHeader("User-Agent")).getBrowser();
            //获取浏览器版本号
            Version version = browser.getVersion(request.getHeader("User-Agent"));
            info = browser.getName() + "/" + version.getVersion();
        }catch(NullPointerException e){
            info = "未知浏览器";
            System.err.println("获取请求浏览器失败");
        }
        return info;
    }
}
