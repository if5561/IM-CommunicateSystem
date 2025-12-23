package com.lld.im.service.utils;

import com.alibaba.fastjson.JSONObject;
import com.lld.im.codec.pack.group.AddGroupMemberPack;
import com.lld.im.codec.pack.group.RemoveGroupMemberPack;
import com.lld.im.codec.pack.group.UpdateGroupMemberPack;
import com.lld.im.common.ClientType;
import com.lld.im.common.enums.command.Command;
import com.lld.im.common.enums.command.GroupEventCommand;
import com.lld.im.common.model.ClientInfo;
import com.lld.im.service.group.model.req.GroupMemberDto;
import com.lld.im.service.group.service.ImGroupMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GroupMessageProducer {

    @Autowired
    MessageProducer messageProducer;

    @Autowired
    ImGroupMemberService imGroupMemberService;

    //群消息需要发给群里所有人，但不能让发送者自己重复收到自己发的消息（只同步到自己的其他设备）
    public void producer(String userId, Command command, Object data,
                         ClientInfo clientInfo){
        JSONObject o = (JSONObject) JSONObject.toJSON(data);
        String groupId = o.getString("groupId");
        List<String> groupMemberId = imGroupMemberService
                .getGroupMemberId(groupId, clientInfo.getAppId());
        if(command.equals(GroupEventCommand.ADDED_MEMBER)){
            //加入群成员 只发给管理员和新加入的群成员
            List<GroupMemberDto> groupManager = imGroupMemberService
                    .getGroupManager(groupId, clientInfo.getAppId());
            AddGroupMemberPack addGroupMemberPack
                    = o.toJavaObject(AddGroupMemberPack.class);
            List<String> members = addGroupMemberPack.getMembers();//获取新加入的群成员
            for (GroupMemberDto groupMemberDto : groupManager) {
                if(clientInfo.getClientType() != ClientType.WEBAPI.getCode() && groupMemberDto.getMemberId().equals(userId)){
                    messageProducer.sendToUserExceptClient(groupMemberDto.getMemberId(),command,data,clientInfo);
                }else{
                    messageProducer.sendToUser(groupMemberDto.getMemberId(),command,data,clientInfo.getAppId());
                }
            }
            for (String member : members) {
                if(clientInfo.getClientType() != ClientType.WEBAPI.getCode() && member.equals(userId)){
                    messageProducer.sendToUserExceptClient(member,command,data,clientInfo);
                }else{
                    messageProducer.sendToUser(member,command,data,clientInfo.getAppId());
                }
            }

        } else if (command.equals(GroupEventCommand.DELETED_MEMBER)) {
            //查出所有群成员（包括被踢成员）再找到被踢的群成员 发送tcp通知
            //踢人 要发送给被踢人
            RemoveGroupMemberPack pack = o.toJavaObject(RemoveGroupMemberPack.class);
            String member = pack.getMember();
            List<String> members = imGroupMemberService.getGroupMemberId(groupId, clientInfo.getAppId());
            members.add(member);
            for (String memberId : members) {                           //被踢人 ID == 操作人 ID（自己踢自己）
                if(clientInfo.getClientType() != ClientType.WEBAPI.getCode() && member.equals(userId)){
                    messageProducer.sendToUserExceptClient(memberId,command,data,clientInfo);
                }else{
                    messageProducer.sendToUser(memberId,command,data,clientInfo.getAppId());
                }
            }
        } else if (command.equals(GroupEventCommand.UPDATED_MEMBER)) {
            //修改群成员 发送给管理员和本人
            UpdateGroupMemberPack pack =
                    o.toJavaObject(UpdateGroupMemberPack.class);
            String memberId = pack.getMemberId();
            List<GroupMemberDto> groupManager = imGroupMemberService.getGroupManager(groupId, clientInfo.getAppId());
            GroupMemberDto groupMemberDto = new GroupMemberDto();
            groupMemberDto.setMemberId(memberId);
            groupManager.add(groupMemberDto);
            for (GroupMemberDto member : groupManager) {
                if(clientInfo.getClientType() != ClientType.WEBAPI.getCode() && member.equals(userId)){
                    messageProducer.sendToUserExceptClient(member.getMemberId(),command,data,clientInfo);
                }else{
                    messageProducer.sendToUser(member.getMemberId(),command,data,clientInfo.getAppId());
                }
            }
        }else {
            for (String memberId : groupMemberId) {
                if (clientInfo.getClientType() != null && clientInfo.getClientType()
                        != ClientType.WEBAPI.getCode() && memberId.equals(userId)) {
                    //不是webapi发起的 （说明是 App/PC 等客户端发起的消息） 并且判断当前循环的id是否是自己
                    messageProducer.sendToUserExceptClient(memberId,
                            command, data, clientInfo);//发送给该用户的其他端
                } else {
                    //不是自己
                    messageProducer.sendToUser(memberId, command, data, clientInfo.getAppId());
                }
            }
        }
    }
}
