package com.lld.im.common.route;

import java.util.List;

public interface RouteHandle {

    public String routeServer(List<String> values,String key);
    //根据key取出一个可用的server服务器地址

}
