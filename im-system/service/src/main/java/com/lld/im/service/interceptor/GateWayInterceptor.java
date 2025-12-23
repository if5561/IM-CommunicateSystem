package com.lld.im.service.interceptor;

import com.alibaba.fastjson.JSONObject;
import com.lld.im.common.BaseErrorCode;
import com.lld.im.common.ResponseVO;
import com.lld.im.common.enums.GateWayErrorCode;
import com.lld.im.common.exception.ApplicationExceptionEnum;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

@Component
public class GateWayInterceptor implements HandlerInterceptor {

    @Autowired
    IdentityCheck identityCheck;

    //userSign的生成方法
    //appService(带上生成UserSign的方法)->返回给im->请求im接口连接im服务器
    //参考腾讯云，可以去官网查看
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if(1==1){return true;}

        //获取appId 操作人 userSign
        String appIdStr = request.getParameter("appId");
        if(StringUtils.isBlank(appIdStr)){
            resp(ResponseVO.errorResponse(GateWayErrorCode
                    .APPID_NOT_EXIST),response);
            return false;
        }
        String identifier = request.getParameter("identifier");
        if(StringUtils.isBlank(identifier)){
            resp(ResponseVO.errorResponse(GateWayErrorCode
                    .OPERATER_NOT_EXIST),response);
            return false;
        }
        String userSign = request.getParameter("userSign");
        if(StringUtils.isBlank(userSign)){
            resp(ResponseVO.errorResponse(GateWayErrorCode
                    .USERSIGN_NOT_EXIST),response);
            return false;
        }

        //签名和操作人 appId是否匹配
        ApplicationExceptionEnum applicationExceptionEnum = identityCheck.checkUserSig(identifier, appIdStr, userSign);
        if(applicationExceptionEnum != BaseErrorCode.SUCCESS){
            resp(ResponseVO.errorResponse(applicationExceptionEnum),response);
            return false;
        }

        return true;
    }

    private void resp(ResponseVO respVo,HttpServletResponse response){
        //返回错误信息给调用方
        PrintWriter writer =null;//回写数据的对象
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=utf-8");
        try {
            String resp = JSONObject.toJSONString(respVo);

            writer = response.getWriter();
            writer.write(resp);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(writer!=null){
                writer.close();
            }
        }
    }
}
