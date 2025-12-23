package com.lld.im.service.group.service;

import com.lld.im.common.ResponseVO;
import com.lld.im.service.group.dao.ImGroupMemberEntity;
import com.lld.im.service.group.model.req.*;
import com.lld.im.service.group.model.resp.GetRoleInGroupResp;

import javax.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;

public interface ImGroupMemberService {

    //导入群成员
    public ResponseVO importGroupMember(ImportGroupMemberReq req);

    //导入群成员 逻辑方法 方法之间调用
    public ResponseVO addGroupMember(String groupId, Integer appId, GroupMemberDto dto);

    //获取成员在当前群的权限
    public ResponseVO<GetRoleInGroupResp> getRoleInGroupOne(String groupId, String memberId, Integer appId);

    //获取群里所有群成员
    public ResponseVO<List<GroupMemberDto>> getGroupMember(String groupId, Integer appId);

    //获取用户加入的所有群id
    public ResponseVO<Collection<String>> getMemberJoinedGroup(GetJoinedGroupReq req);

    //修改成员和群主的身份（转让群主的员工表方法）--内部调用
    public ResponseVO transferGroupMember(String owner, String groupId, Integer appId);

    //拉人入群
    public ResponseVO addMember(AddGroupMemberReq req);

    //踢出群聊
    ResponseVO removeMember(RemoveGroupMemberReq req);

    //删除群成员，内部调用
    public ResponseVO removeGroupMember(String groupId, Integer appId, String memberId);

    //修改群成员信息
    ResponseVO updateGroupMember(UpdateGroupMemberReq req);

    //禁言群成员
    public ResponseVO speak(SpeaMemberReq req);

    //批量获取memberId
    public List<String> getGroupMemberId(String groupId, Integer appId);

    //获取管理员的方法
    public List<GroupMemberDto> getGroupManager(String groupId, Integer appId);

    //同步群成员加入了哪些群聊
    ResponseVO<Collection<String>> syncMemberJoinedGroup(String operater, Integer appId);
}
