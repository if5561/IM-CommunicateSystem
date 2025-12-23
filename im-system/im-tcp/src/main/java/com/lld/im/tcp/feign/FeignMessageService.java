package com.lld.im.tcp.feign;

import com.lld.im.common.ResponseVO;
import com.lld.im.common.model.message.CheckSendMessageReq;
import feign.Headers;
import feign.RequestLine;

public interface FeignMessageService {

    @Headers({"Content-Type: application/json","Accept: application/json"})
    @RequestLine("POST /message/checkSend")//接口地址
    public ResponseVO checkSendMessage(CheckSendMessageReq o);

}
