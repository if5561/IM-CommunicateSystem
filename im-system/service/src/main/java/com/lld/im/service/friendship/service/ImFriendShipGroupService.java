package com.lld.im.service.friendship.service;

import com.lld.im.common.ResponseVO;
import com.lld.im.service.friendship.dao.ImFriendShipGroupEntity;
import com.lld.im.service.friendship.model.req.AddFriendShipGroupReq;
import com.lld.im.service.friendship.model.req.DeleteFriendShipGroupReq;

public interface ImFriendShipGroupService {

    //添加分组
    public ResponseVO addGroup(AddFriendShipGroupReq req);

    //删除分组
    public ResponseVO deleteGroup(DeleteFriendShipGroupReq req);

    //查询分组
    public ResponseVO<ImFriendShipGroupEntity> getGroup(String fromId, String groupName, Integer appId);

}
