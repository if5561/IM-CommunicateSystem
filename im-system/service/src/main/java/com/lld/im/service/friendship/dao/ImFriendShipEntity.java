package com.lld.im.service.friendship.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.jeffreyning.mybatisplus.anno.AutoMap;
import lombok.Data;

/**
 * @author: Chackylee
 * @description:
 **/

@Data
@TableName("im_friendship")
@AutoMap
public class ImFriendShipEntity {

    @TableId(value = "app_id", type = IdType.INPUT)
    private Integer appId;

    @TableField("from_id")
    private String fromId;

    @TableField("to_id")
    private String toId;
    /** 备注*/
    private String remark;
    /** 状态 1正常 2删除*/
    private Integer status;
    /** 状态 1正常 2拉黑*/
    private Integer black;
    //    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long createTime;
    /** 好友关系序列号*/
//    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long friendSequence;

    /** 黑名单关系序列号*/
    private Long blackSequence;
    /** 好友来源*/
//    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String addSource;

    private String extra;

}
