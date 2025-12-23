package com.lld.im.service.utils;

import com.lld.im.common.Constants.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * @description:
 * @author: lld
 * @version: 1.0
 */
@Service
public class WriteUserSeq {

    //redis   uid是redis的key friend/group这种类型作为hash 具体的seq作为value
    //uid friend 10
    //    group 12
    //    conversation 123
    @Autowired
    RedisTemplate redisTemplate;

    public void writeUserSeq(Integer appId,String userId,String type,Long seq){
        String key = appId + ":" + Constants.RedisConstants.SeqPrefix + ":" + userId;
        redisTemplate.opsForHash().put(key,type,seq);
    }

}
