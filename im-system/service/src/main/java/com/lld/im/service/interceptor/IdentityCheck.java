package com.lld.im.service.interceptor;

import com.alibaba.fastjson.JSONObject;
import com.lld.im.common.BaseErrorCode;
import com.lld.im.common.Constants.Constants;
import com.lld.im.common.config.AppConfig;
import com.lld.im.common.enums.GateWayErrorCode;
import com.lld.im.common.exception.ApplicationExceptionEnum;
import com.lld.im.common.utils.SigAPI;
import com.lld.im.service.user.service.ImUserService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.Signature;
import java.util.concurrent.TimeUnit;

@Component
public class IdentityCheck {

    private static Logger logger = LoggerFactory.getLogger (IdentityCheck.class);

    @Autowired
    ImUserService imUserService;

    //用配置文件 代替密钥之间的关系映射（原本是一张表）
    @Autowired
    AppConfig appConfig;

    @Autowired
    StringRedisTemplate stringRedisTemplate;


    public ApplicationExceptionEnum checkUserSig(String identifier,
                                                 String appId,String userSig){
        //从redis取出密钥
        String cacheUserSig = stringRedisTemplate.opsForValue().get(appId + ":" + Constants.RedisConstants.userSign + ":"
                + identifier + userSig);
        // redis的value是过期时间
        if (! StringUtils.isBlank (cacheUserSig) && Long.valueOf (cacheUserSig)
                > System.currentTimeMillis () / 1000){

            return BaseErrorCode.SUCCESS;
        }

        //获取密钥
        String privateKey = appConfig.getPrivateKey();

        //根据appid+密钥创建sigApi
        //SigAPI sigAPI = new SigAPI(Long.valueOf(appId), privateKey);

        //调用sigApi对userSig解密
        JSONObject jsonObject = SigAPI.decodeUserSig(userSig);

        //取出解密后的appid 和 操作人 和 过期时间做匹配 不通过则提示错误
        Long expireTime = 0L;
        Long expireSec = 0L;//过期的秒数
        //Long time = 0L;
        String decoerAppId ="";
        String decoderidentifier = "";

        try {
            decoerAppId = jsonObject.getString ("TLS.appId");
            decoderidentifier = jsonObject.getString ("TLS.identifier");
            String expireStr = jsonObject.get ("TLS.expire").toString ();
            String expireTimeStr = jsonObject.get ("TLS.expireTime").toString ();
            //time = Long.valueOf (expireTimeStr);
            expireSec = Long.valueOf (expireStr);
            expireTime = Long.valueOf (expireTimeStr) + expireSec;
        }catch (Exception e){
            e.printStackTrace ();
            logger.error ("checkUserSig-error:{}", e.getMessage ());
        }

        if (! decoderidentifier.equals (identifier)){
            return GateWayErrorCode.USERSIGN_OPERATE_NOT_MATE;
        }

        if (! decoerAppId.equals (appId)){
            return GateWayErrorCode.USERSIGN_IS_ERROR;
        }

        if (expireSec == 0L){
            return GateWayErrorCode.USERSIGN_IS_EXPIRED;
        }
        //过期时间
        if (expireTime < System.currentTimeMillis () / 1000){
            return GateWayErrorCode.USERSIGN_IS_EXPIRED;
        }
        /**
         * userSign，格式：appId:userSign:
         */
        //appid + "xxx" + userId + sign
        String key = appId + ":" + Constants.RedisConstants.userSign + ":"
                + identifier + userSig;
        Long etime = expireTime - System.currentTimeMillis () / 1000;
        stringRedisTemplate.opsForValue ().set (
                key, expireTime.toString (), etime, TimeUnit.SECONDS
        );//key传入redis
        //用 stringRedisTemplate 往 Redis 中存储一个「字符串类型」的键值对，
        // 并且给这个键（key）设置了「过期时间」（单位：秒）
        return BaseErrorCode.SUCCESS;
    }

}
