package com.shiku.mianshi.controller;

import cn.xyz.commons.constants.KConstants;
import cn.xyz.commons.ex.ServiceException;
import cn.xyz.commons.utils.ReqUtil;
import cn.xyz.commons.vo.JSONMessage;
import cn.xyz.mianshi.model.PageResult;
import cn.xyz.mianshi.service.impl.UserManagerImpl;
import cn.xyz.mianshi.utils.SKBeanUtils;
import cn.xyz.mianshi.vo.UserOperationLog;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/console")
public class UserOperationLogController {


    private static UserManagerImpl getUserManager(){
        UserManagerImpl userManager = SKBeanUtils.getUserManager();
        return userManager;
    };

    /**
     * @Description:（用户ip日志查询）
     * @param userId
     * @param ip
     * @param logtype
     * @return
     **/
    @RequestMapping("/findUserOperationLog")
    public JSONMessage findUserOperationLog(@RequestParam(defaultValue = "0") int userId,
                                      @RequestParam(defaultValue = "") Integer logtype, @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int limit, @RequestParam(defaultValue = "") String startDate,
                                      @RequestParam(defaultValue = "") String endDate, @RequestParam(defaultValue = "") String ip,
                                      @RequestParam(defaultValue = "") String telephone,@RequestParam(defaultValue = "") String nickName) {
        try {
            // 权限校验
            byte role = (byte) SKBeanUtils.getRoleManager().getUserRoleByUserId(ReqUtil.getUserId());
            if(role!= KConstants.Admin_Role.SUPER_ADMIN&&role!=KConstants.Admin_Role.FINANCE){
                return JSONMessage.failure("权限不足");
            }
            PageResult<UserOperationLog> result = SKBeanUtils.getUserOperationLogManager().findOperationLog(userId, logtype, page, limit,
                    startDate, endDate ,ip,telephone,nickName);
            return JSONMessage.success(result);
        } catch (ServiceException e) {
            return JSONMessage.failure(e.getErrMessage());
        }
    }



    @RequestMapping(value = "/getUserOperatioIp")
    public JSONMessage getUserOperatioIp(Integer userId){
        userId=null!=userId?userId:ReqUtil.getUserId();
        Object data= SKBeanUtils.getRedisService().getUserOperatioIp(userId);
        return JSONMessage.success(null, data);
    }
}
