package com.lld.im.common.route.algorithm.consistenthash;

import com.lld.im.common.enums.UserErrorCode;
import com.lld.im.common.exception.ApplicationException;
import com.lld.im.common.route.RouteHandle;

import java.util.List;

public class ConsistentHashHandle implements RouteHandle {

    private AbstractConsistentHash hash;

    public void setHash(AbstractConsistentHash hash) {
        this.hash = hash;
    }

    @Override
    public String routeServer(List<String> values, String key) {
        int size=values.size();
        if(size==0){
            throw new ApplicationException(UserErrorCode.SERVER_NOT_AVAILABLE);
        }
        //根据哈希值key 从服务列表中取出地址  调用抽象类的 process 方法
        return hash.process(values,key);
    }
}
