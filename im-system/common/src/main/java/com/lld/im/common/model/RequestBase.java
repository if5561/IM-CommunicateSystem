package com.lld.im.common.model;

import lombok.Data;

@Data
public class RequestBase {

    private Integer appId;

    private String operater;//操作人 是谁在调用这个接口

    private Integer clientType;

    private String imei;

}
