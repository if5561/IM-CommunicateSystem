package com.lld.im.common.model.message;

import lombok.Data;

/**
 * @description: 属性和messageBodyEntity一样
 * @author: lld
 * @version: 1.0
 */
@Data
public class ImMessageBody {
    private Integer appId;

    /** messageBodyId*/
    private Long messageKey;

    /** messageBody*/
    private String messageBody;

    private String securityKey;

    private Long messageTime;

    private Long createTime;

    private String extra;

    private Integer delFlag;
}
