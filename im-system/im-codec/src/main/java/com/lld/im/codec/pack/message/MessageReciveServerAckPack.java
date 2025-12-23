package com.lld.im.codec.pack.message;

import lombok.Data;

/**
 * @description: 服务端发起的ack
 * @author: lld
 * @version: 1.0
 */
@Data
public class MessageReciveServerAckPack {

    private Long messageKey;

    private String fromId;

    private String toId;

    private Long messageSequence;

    private Boolean serverSend;//表示是否是服务端发起的ack
}
