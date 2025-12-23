package com.lld.im.service.friendship.service;

import com.lld.im.common.ResponseVO;
import com.lld.im.service.friendship.model.req.ApproverFriendRequestReq;
import com.lld.im.service.friendship.model.req.FriendDto;
import com.lld.im.service.friendship.model.req.ReadFriendShipRequestReq;

public interface ImFriendShipRequestService {

    //插入好友申请
    public ResponseVO addFriendshipRequest(String fromId, FriendDto dto,Integer appId);

    //好友申请 审批
    public ResponseVO approverFriendRequest(ApproverFriendRequestReq req);

    //好友已读---“我们”来已读好友申请 fromId和toId要交换一下
    ResponseVO readFriendShipRequestReq(ReadFriendShipRequestReq req);

    //获取所有好友申请记录---有个相反的逻辑 要获取发送给“我们”的好友数据
    ResponseVO getFriendRequest(String fromId,Integer appId);
}
