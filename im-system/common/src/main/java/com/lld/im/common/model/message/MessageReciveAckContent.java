package com.lld.im.common.model.message;

import com.lld.im.common.model.ClientInfo;
import lombok.Data;


/**
 * @description:
 * @author: lld
 * @version: 1.0
 */
@Data
public class MessageReciveAckContent extends ClientInfo {
//要确认一条消息 需要：
    private Long messageKey;

    private String fromId;

    private String toId;

    private Long messageSequence;


}
