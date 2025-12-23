package com.lld.im.service.friendship.service;

import com.lld.im.common.ResponseVO;
import com.lld.im.service.friendship.model.req.AddFriendShipGroupMemberReq;
import com.lld.im.service.friendship.model.req.DeleteFriendShipGroupMemberReq;

/**
 * @author: Chackylee
 * @description:
 **/
public interface ImFriendShipGroupMemberService {
    //添加分组成员
    public ResponseVO addGroupMember(AddFriendShipGroupMemberReq req);

    //删除分组成员
    public ResponseVO delGroupMember(DeleteFriendShipGroupMemberReq req);

    //添加分组成员的实现方法
    public int doAddGroupMember(Long groupId, String toId);

    //清除组内成员
    public int clearGroupMember(Long groupId);
}
