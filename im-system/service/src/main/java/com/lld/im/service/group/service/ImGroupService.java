package com.lld.im.service.group.service;

import com.lld.im.common.ResponseVO;
import com.lld.im.common.model.SyncReq;
import com.lld.im.service.group.dao.ImGroupEntity;
import com.lld.im.service.group.model.req.*;

public interface ImGroupService {

    //导入群组
    public ResponseVO importGroup(ImportGroupReq req);

    //查询群是否存在
    public ResponseVO<ImGroupEntity> getGroup(String groupId, Integer appId);

    //创建群组
    public ResponseVO createGroup(CreateGroupReq req);

    //更新群组
    public ResponseVO updateBaseGroupInfo(UpdateGroupReq req);

    //获取群组信息
    ResponseVO getGroupInfo(GetGroupInfoReq req);

    //查询用户进入的群列表
    public ResponseVO getJoinedGroup(GetJoinedGroupReq req);

    //解散群组
    ResponseVO destroyGroup(DestroyGroupReq req);

    //转让群组
    ResponseVO transferGroup(TransferGroupReq req);

    //禁言群
    public ResponseVO muteGroup(MuteGroupReq req);

    //同步加入的群聊的 seq
    ResponseVO syncJoinedGroupList(SyncReq req);

    //获取用户加入群组的最大seq
    Long getUserGroupMaxSeq(String userId, Integer appId);
}
