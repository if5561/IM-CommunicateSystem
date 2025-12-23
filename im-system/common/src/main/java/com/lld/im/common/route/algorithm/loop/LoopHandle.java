package com.lld.im.common.route.algorithm.loop;

import com.lld.im.common.enums.UserErrorCode;
import com.lld.im.common.exception.ApplicationException;
import com.lld.im.common.route.RouteHandle;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class LoopHandle implements RouteHandle {

    private AtomicLong index=new AtomicLong();
    //保证线程安全的同时递增

    @Override
    public String routeServer(List<String> values, String key) {
        int size = values.size();
        if(size == 0){
            throw  new ApplicationException(UserErrorCode.SERVER_NOT_AVAILABLE);
        }
        Long l = index.incrementAndGet() % size;
        //原子操作，先自增 1，再返回新值
        if(l<0){
            l=0L;
        }
        return values.get(l.intValue());
    }
}
