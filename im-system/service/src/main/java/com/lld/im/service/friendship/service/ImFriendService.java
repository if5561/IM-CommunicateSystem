package com.lld.im.service.friendship.service;

import com.lld.im.common.ResponseVO;
import com.lld.im.common.model.RequestBase;
import com.lld.im.common.model.SyncReq;
import com.lld.im.service.friendship.model.req.*;

import java.util.List;

public interface ImFriendService {
    //导入关系链
    public ResponseVO importFriendShip(ImportFriendShipReq req);

    //添加好友（查找是否存在）
    public ResponseVO addFriend(AddFriendReq req);
    //添加好友（添加逻辑）
    public ResponseVO doAddFriend(RequestBase requestBase, String fromId, com.lld.im.service.friendship.model.req.FriendDto dto, Integer appId);

    //修改好友
    public ResponseVO updateFriend(UpdateFriendReq req);

    //删除好友
    public ResponseVO deleteFriend(DeleteFriendReq req);

    //删除所有好友
    public ResponseVO deleteAllFriend(DeleteFriendReq req);

    //获取所有好友
    public ResponseVO getAllFriendShip(GetAllFriendShipReq req);

    //获取指定好友
    public ResponseVO getRelation(GetRelationReq req);

    //校验好友
    public ResponseVO checkFriendShip(CheckFriendShipReq req);

    //添加黑名单
    public ResponseVO addBlack(AddFriendShipBlackReq req);

    //删除黑名单
    public ResponseVO deleteBlack(DeleteBlackReq req);

    //校验黑名单
    public ResponseVO checkBlack(CheckFriendShipReq req);


    public ResponseVO syncFriendshipList(SyncReq req);

    public List<String> getAllFriendId(String userId, Integer appId);

}
