package com.lld.im.service.config;

import com.lld.im.common.config.AppConfig;
import com.lld.im.common.enums.ImUrlRouteWayEnum;
import com.lld.im.common.enums.RouteHashMethodEnum;
import com.lld.im.common.route.RouteHandle;
import com.lld.im.common.route.algorithm.consistenthash.AbstractConsistentHash;
import com.lld.im.common.route.algorithm.consistenthash.ConsistentHashHandle;
import com.lld.im.common.route.algorithm.consistenthash.TreeMapConsistentHash;
import com.lld.im.common.route.algorithm.loop.LoopHandle;
import com.lld.im.common.route.algorithm.random.RandomHandle;
import com.lld.im.service.utils.SnowflakeIdWorker;
import org.I0Itec.zkclient.ZkClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;

@Configuration
public class BeanConfig {

    @Autowired
    AppConfig appConfig;

    @Bean
    public ZkClient buildZKClient() {
        return new ZkClient(appConfig.getZkAddr(),
                appConfig.getZkConnectTimeOut());
    }

    @Bean
    public RouteHandle routeHandle() throws Exception {
        Integer imRouteWay = appConfig.getImRouteWay();
        String routWay = "";

        ImUrlRouteWayEnum handler = ImUrlRouteWayEnum.getHandler(imRouteWay);
        routWay = handler.getClazz();//得到全路径名

        RouteHandle routeHandle = (RouteHandle) Class.forName(routWay).newInstance();

        if(handler == ImUrlRouteWayEnum.HASH){

            Method setHash = Class.forName(routWay).getMethod("setHash", AbstractConsistentHash.class);
            Integer consistentHashWay = appConfig.getConsistentHashWay();
            String hashWay = "";

            RouteHashMethodEnum hashHandler = RouteHashMethodEnum.getHandler(consistentHashWay);
            hashWay = hashHandler.getClazz();
            AbstractConsistentHash consistentHash
                    = (AbstractConsistentHash) Class.forName(hashWay).newInstance();
            setHash.invoke(routeHandle,consistentHash);
        }   //如果采用一致性哈希算法还要额外判断 ↑

        return routeHandle;
    }

    @Bean
    public EasySqlInjector easySqlInjector () {
        return new EasySqlInjector();
    }

    @Bean
    public SnowflakeIdWorker buildSnowflakeSeq() throws Exception {
        return new SnowflakeIdWorker(0);
    }
}
