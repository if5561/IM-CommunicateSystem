package com.lld.im.service.friendship.model.req;

import com.lld.im.common.model.RequestBase;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class DeleteFriendReq extends RequestBase {
    @NotBlank(message = "fromId不能为空")
    private String fromId;
    //删除谁的好友
    @NotBlank(message = "toId不能为空")
    private String toId;
    //删除哪位好友

}
