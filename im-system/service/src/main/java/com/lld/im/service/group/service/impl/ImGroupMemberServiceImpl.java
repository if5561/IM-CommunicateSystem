package com.lld.im.service.group.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lld.im.common.Constants.Constants;
import com.lld.im.common.ResponseVO;
import com.lld.im.common.config.AppConfig;
import com.lld.im.common.enums.GroupErrorCode;
import com.lld.im.common.enums.GroupMemberRoleEnum;
import com.lld.im.common.enums.GroupStatusEnum;
import com.lld.im.common.enums.GroupTypeEnum;
import com.lld.im.common.exception.ApplicationException;
import com.lld.im.service.group.dao.ImGroupEntity;
import com.lld.im.service.group.dao.ImGroupMemberEntity;
import com.lld.im.service.group.dao.mapper.ImGroupMemberMapper;
import com.lld.im.service.group.model.callback.AddMemberAfterCallback;
import com.lld.im.service.group.model.req.*;
import com.lld.im.service.group.model.resp.AddMemberResp;
import com.lld.im.service.group.model.resp.GetRoleInGroupResp;
import com.lld.im.service.group.service.ImGroupMemberService;
import com.lld.im.service.group.service.ImGroupService;
import com.lld.im.service.user.dao.ImUserDataEntity;
import com.lld.im.service.user.service.ImUserService;
import com.lld.im.service.utils.CallbackService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
@Slf4j
@Service
public class ImGroupMemberServiceImpl implements ImGroupMemberService {

    @Autowired
    ImGroupService groupService;

    @Autowired
    ImGroupMemberMapper imGroupMemberMapper;

    @Autowired
    ImGroupMemberService groupMemberService;

    @Autowired
    ImUserService imUserService;

    @Autowired
    AppConfig appConfig;

    @Autowired
    CallbackService callbackService;


    @Override
    public ResponseVO importGroupMember(ImportGroupMemberReq req) {

        List<AddMemberResp> resp =new ArrayList<>();//返回集合 表示多个成员导入的成功/失败原因
        ResponseVO<ImGroupEntity> groupResp = groupService.getGroup(req.getGroupId(), req.getAppId());
        if (!groupResp.isOk()) {//群不存在
            return groupResp;
        }
        for (GroupMemberDto memberId : //memberId 是集合中的每个成员的信息类dto
                req.getMembers()) {
            //插入逻辑
            ResponseVO responseVO = null;
            try {
                responseVO = groupMemberService.addGroupMember(req.getGroupId(), req.getAppId(), memberId);
            } catch (Exception e) {
                e.printStackTrace();
                responseVO = ResponseVO.errorResponse();
            }
            AddMemberResp addMemberResp = new AddMemberResp();
            addMemberResp.setMemberId(memberId.getMemberId());
            if (responseVO.isOk()) {
                addMemberResp.setResult(0);//成功
            } else if (responseVO.getCode() == GroupErrorCode.USER_IS_JOINED_GROUP.getCode()) {
                addMemberResp.setResult(2);//已在群内
            } else {
                addMemberResp.setResult(1);//添加失败
            }
            resp.add(addMemberResp);
        }
        return ResponseVO.successResponse(resp);
    }

     /**
     //     * @param
     //     * @return com.lld.im.common.ResponseVO
     //     * @description: 添加群成员，内部调用
     //     * @author lld
     //     */
    @Override
    @Transactional
    public ResponseVO addGroupMember(String groupId, Integer appId, GroupMemberDto dto) {

        ResponseVO<ImUserDataEntity> singleUserInfo = imUserService.getSingleUserInfo(dto.getMemberId(), appId);
        if(!singleUserInfo.isOk()){
            return singleUserInfo;
        }

        if (dto.getRole() != null && GroupMemberRoleEnum.OWNER.getCode() == dto.getRole()) {
            QueryWrapper<ImGroupMemberEntity> queryOwner = new QueryWrapper<>();
            queryOwner.eq("group_id", groupId);
            queryOwner.eq("app_id", appId);
            queryOwner.eq("role", GroupMemberRoleEnum.OWNER.getCode());
            Integer ownerNum = imGroupMemberMapper.selectCount(queryOwner);
            if (ownerNum > 0) {//这个人是群主 并且群已经有群主了 返回错误
                return ResponseVO.errorResponse(GroupErrorCode.GROUP_IS_HAVE_OWNER);
            }
        }

        QueryWrapper<ImGroupMemberEntity> query = new QueryWrapper<>();
        query.eq("group_id", groupId);
        query.eq("app_id", appId);
        query.eq("member_id", dto.getMemberId());
        ImGroupMemberEntity memberDto = imGroupMemberMapper.selectOne(query);

        long now = System.currentTimeMillis();
        if (memberDto == null) {
            //初次加群 群内没有这个人 走插入的逻辑
            memberDto = new ImGroupMemberEntity();
            BeanUtils.copyProperties(dto, memberDto);
            memberDto.setGroupId(groupId);
            memberDto.setAppId(appId);
            memberDto.setJoinTime(now);
            int insert = imGroupMemberMapper.insert(memberDto);
            if (insert == 1) {
                return ResponseVO.successResponse();
            }
            return ResponseVO.errorResponse(GroupErrorCode.USER_JOIN_GROUP_ERROR);
        } else if (GroupMemberRoleEnum.LEAVE.getCode() == memberDto.getRole()) {
            //重新进群 退过群的
            memberDto = new ImGroupMemberEntity();
            BeanUtils.copyProperties(dto, memberDto);//这里设置回了原来的状态（普通成员） dto中有role这个参数 传参进来时会设置
            memberDto.setJoinTime(now);
            int update = imGroupMemberMapper.update(memberDto, query);
            if (update == 1) {
                return ResponseVO.successResponse();
            }
            return ResponseVO.errorResponse(GroupErrorCode.USER_JOIN_GROUP_ERROR);
        }
        return ResponseVO.errorResponse(GroupErrorCode.USER_IS_JOINED_GROUP);
    }

    //查询获取成员在当前群的权限（角色）
    @Override
    public ResponseVO<GetRoleInGroupResp> getRoleInGroupOne(String groupId, String memberId, Integer appId) {
        GetRoleInGroupResp resp = new GetRoleInGroupResp();
        QueryWrapper<ImGroupMemberEntity> query = new QueryWrapper<>();
        query.eq("group_id",groupId);
        query.eq("member_id",memberId);
        query.eq("app_id",appId);
        ImGroupMemberEntity entity = imGroupMemberMapper.selectOne(query);
        if(entity==null||entity.getRole()==GroupMemberRoleEnum.LEAVE.getCode()){
            return ResponseVO.errorResponse(GroupErrorCode.MEMBER_IS_NOT_JOINED_GROUP);
        }
        BeanUtils.copyProperties(entity,resp);
        return ResponseVO.successResponse(resp);
    }

    //获取群里所有群成员（获取群成员信息）
    @Override
    public ResponseVO<List<GroupMemberDto>> getGroupMember(String groupId, Integer appId) {
        List<GroupMemberDto> groupMember = imGroupMemberMapper.getGroupMember(appId, groupId);
        return ResponseVO.successResponse(groupMember);
    }


    //获取用户加入的所有群id
    @Override
    public ResponseVO<Collection<String>> getMemberJoinedGroup(GetJoinedGroupReq req) {
        return imGroupMemberMapper.getJoinedGroupId(req);
    }

    @Override
    @Transactional //更新群主 转让群主操作时内部调用
    public ResponseVO transferGroupMember(String owner, String groupId, Integer appId) {

        //更新旧群主
        ImGroupMemberEntity imGroupMemberEntity = new ImGroupMemberEntity();
        imGroupMemberEntity.setRole(GroupMemberRoleEnum.ORDINARY.getCode());
        UpdateWrapper<ImGroupMemberEntity> updateWrapper =new UpdateWrapper<>();
        updateWrapper.eq("app_id", appId);
        updateWrapper.eq("group_id", groupId);
        updateWrapper.eq("role", GroupMemberRoleEnum.OWNER.getCode());
        imGroupMemberMapper.update(imGroupMemberEntity, updateWrapper);

        //更新新群主
        ImGroupMemberEntity newOwner = new ImGroupMemberEntity();
        newOwner.setRole(GroupMemberRoleEnum.OWNER.getCode());
        UpdateWrapper<ImGroupMemberEntity> ownerWrapper = new UpdateWrapper<>();
        ownerWrapper.eq("app_id", appId);
        ownerWrapper.eq("group_id", groupId);
        ownerWrapper.eq("member_id", owner);
        imGroupMemberMapper.update(newOwner, ownerWrapper);

        return ResponseVO.successResponse();

    }
    /**
     * @param
     * @return com.lld.im.common.ResponseVO
     * @description: 添加群成员，拉人入群的逻辑，直接进入群聊。如果是后台管理员，则直接拉入群，
     * 否则只有私有群可以调用本接口，并且群成员也可以拉人入群.只有私有群可以调用本接口
     * @author lld
     */
    //拉人入群
    @Override
    public ResponseVO addMember(AddGroupMemberReq req) {
        List<AddMemberResp> resp = new ArrayList<>();
        boolean isAdmin = false;
        ResponseVO<ImGroupEntity> groupResp = groupService.getGroup(req.getGroupId(), req.getAppId());
        if(!groupResp.isOk()){
            return groupResp;
        }
        //之前回调有干预性质  供用户筛选哪些人加上好友
        List<GroupMemberDto> memberDtos = req.getMembers();
        if(appConfig.isAddGroupMemberBeforeCallback()){

            ResponseVO responseVO = callbackService.beforeCallback(req.getAppId(),
                    Constants.CallbackCommand.GroupMemberAddBefore, JSONObject.toJSONString(req));
            if(!responseVO.isOk()){
                return responseVO;
            }

            try{     //把 responseVO.getData() 里的「群成员集合数据」（JSON 格式）转成你业务中的 List<GroupMemberDto> 集合
                memberDtos =
                        JSONArray.parseArray(JSONObject.toJSONString(responseVO.getData()), GroupMemberDto.class);
            }catch (Exception e){
                e.printStackTrace();
                log.error("GroupMemberAddBefore 回调失败：{}",req.getAppId());
            }
        }

        ImGroupEntity group = groupResp.getData();

        /**
         * 私有群（private）	类似普通微信群，创建后仅支持已在群内的好友邀请加群，且无需被邀请方同意或群主审批
         * 公开群（Public）	类似 QQ 群，创建后群主可以指定群管理员，需要群主或管理员审批通过才能入群
         * 群类型 1私有群（类似微信） 2公开群(类似qq）
         *
         */
        if(!isAdmin && GroupTypeEnum.PUBLIC.getCode()==group.getGroupType()){
            throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
        }
        List<String> successId = new ArrayList<>();
        for(GroupMemberDto memberId : memberDtos){//循环加入群组
            ResponseVO responseVO =null;
            try {
                responseVO = groupMemberService.addGroupMember(req.getGroupId(), req.getAppId(), memberId);
            } catch (Exception e) {
                e.printStackTrace();
                responseVO = ResponseVO.errorResponse();
            }
            AddMemberResp addMemberResp = new AddMemberResp();
            addMemberResp.setMemberId(memberId.getMemberId());
            if (responseVO.isOk()) {
                successId.add(memberId.getMemberId());
                addMemberResp.setResult(0);// 加人结果：0 为成功；1 为失败；2 为已经是群成员
            } else if (responseVO.getCode() == GroupErrorCode.USER_IS_JOINED_GROUP.getCode()) {
                addMemberResp.setResult(2);
                addMemberResp.setResultMessage(responseVO.getMsg());
            } else {
                addMemberResp.setResult(1);
                addMemberResp.setResultMessage(responseVO.getMsg());
            }
            resp.add(addMemberResp);
        }
        //之后回调
        if(appConfig.isAddGroupMemberAfterCallback()){
            AddMemberAfterCallback dto = new AddMemberAfterCallback();
            dto.setGroupId(req.getGroupId());
            dto.setGroupType(group.getGroupType());
            dto.setMemberId(resp);
            dto.setOperater(req.getOperater());
            callbackService.callback(req.getAppId()
                    ,Constants.CallbackCommand.GroupMemberAddAfter,
                    JSONObject.toJSONString(dto));
        }

        return ResponseVO.successResponse(resp);
    }

    //踢出群聊
    @Override
    public ResponseVO removeMember(RemoveGroupMemberReq req) {
        List<AddMemberResp> resp = new ArrayList<>();
        boolean isAdmin = false;
        ResponseVO<ImGroupEntity> groupResp = groupService.getGroup(req.getGroupId(), req.getAppId());
        if (!groupResp.isOk()) {
            return groupResp;//群是否存在
        }

        ImGroupEntity group = groupResp.getData();
        if(!isAdmin){
            //获取操作人的权限 是管理员or群主or群成员
            ResponseVO<GetRoleInGroupResp> role = getRoleInGroupOne(req.getGroupId(), req.getOperater(), req.getAppId());
            if (!role.isOk()) {
                return role;
            }

            GetRoleInGroupResp data = role.getData();
            Integer roleInfo = data.getRole();

            boolean isOwner = roleInfo == GroupMemberRoleEnum.OWNER.getCode();
            boolean isManager = roleInfo == GroupMemberRoleEnum.MAMAGER.getCode();

            if (!isManager && !isOwner) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
            }

            //私有群必须是群主才能踢人
            if(!isOwner&&GroupTypeEnum.PRIVATE.getCode()==group.getGroupType()){
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
            }

            //公开群管理员和群主可踢人，但管理员只能踢普通群成员
            if(GroupTypeEnum.PUBLIC.getCode()==group.getGroupType()){

                //获取被踢人的权限
                ResponseVO<GetRoleInGroupResp> roleInGroupOne = getRoleInGroupOne(req.getGroupId(), req.getMemberId(), req.getAppId());
                if(!roleInGroupOne.isOk()){
                    return roleInGroupOne;
                }
                GetRoleInGroupResp memberRole = roleInGroupOne.getData();
                if(memberRole.getRole()==GroupMemberRoleEnum.OWNER.getCode()){
                    throw new ApplicationException(GroupErrorCode.GROUP_OWNER_IS_NOT_REMOVE);
                }
                //是管理员并且是被踢人不是群成员（群主/管理员），无法操作
                if(isManager && memberRole.getRole() != GroupMemberRoleEnum.ORDINARY.getCode()){
                    throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
                }
            }
        }
        ResponseVO responseVO = groupMemberService.removeGroupMember(req.getGroupId(),req.getAppId(),req.getMemberId());
        if(responseVO.isOk()){

            //之后回调
            if(appConfig.isDeleteGroupMemberAfterCallback()){
                callbackService.callback(req.getAppId(),
                        Constants.CallbackCommand.GroupMemberDeleteAfter,
                        JSONObject.toJSONString(req));
            }
        }
        return responseVO;
    }

    /**
     * @param
     * @return com.lld.im.common.ResponseVO
     * @description: 删除群成员，内部调用
     * @author lld
     */
    @Override
    public ResponseVO removeGroupMember(String groupId, Integer appId, String memberId) {

        ResponseVO<ImUserDataEntity> singleUserInfo = imUserService.getSingleUserInfo(memberId, appId);
        if(!singleUserInfo.isOk()){
            return singleUserInfo;
        }

        ResponseVO<GetRoleInGroupResp> roleInGroupOne = getRoleInGroupOne(groupId, memberId, appId);
        if (!roleInGroupOne.isOk()) {
            return roleInGroupOne;
        }

        GetRoleInGroupResp data = roleInGroupOne.getData();
        ImGroupMemberEntity imGroupMemberEntity = new ImGroupMemberEntity();
        imGroupMemberEntity.setRole(GroupMemberRoleEnum.LEAVE.getCode());
        imGroupMemberEntity.setLeaveTime(System.currentTimeMillis());
        imGroupMemberEntity.setGroupMemberId(data.getGroupMemberId());
        imGroupMemberMapper.updateById(imGroupMemberEntity);
        return ResponseVO.successResponse();
    }
    //没有离开群组的接口方法


    //修改群成员信息
    @Override
    public ResponseVO updateGroupMember(UpdateGroupMemberReq req) {
        boolean isadmin = false;
        //获取群信息
        ResponseVO<ImGroupEntity> group = groupService.getGroup(req.getGroupId(), req.getAppId());
            if(!group.isOk()) {
                return group;
            }
        ImGroupEntity groupData = group.getData();
        if(groupData.getStatus()== GroupStatusEnum.DESTROY.getCode()){
            throw new ApplicationException(GroupErrorCode.GROUP_IS_DESTROY);
        }

        //是否修改自己的群资料
        boolean isMeOperate = req.getOperater().equals(req.getMemberId());

        if (!isadmin) {
            //昵称只能自己修改 权限只能群主或者管理员修改
            if(StringUtils.isBlank(req.getAlias()) && !isMeOperate ){
                return ResponseVO.errorResponse(GroupErrorCode.THIS_OPERATE_NEED_ONESELF);
            }
            //私有群不能设置管理员
            if(groupData.getGroupType()==GroupTypeEnum.PRIVATE.getCode()
                    &&req.getRole() !=null &&(req.getRole()==GroupMemberRoleEnum.MAMAGER.getCode()
                    ||req.getRole() ==GroupMemberRoleEnum.OWNER.getCode())){
                //req.get的含义：req是从客户端接收进来的请求 req.getRole()：获取客户端希望为某个群成员设置的 “角色”。
                return ResponseVO.errorResponse(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
            }
            //需要修改权限 走下面的逻辑
            if(req.getRole()!=null){
                //获取被操作人是否在群内
                ResponseVO<GetRoleInGroupResp> roleInGroupOne = getRoleInGroupOne(req.getGroupId(), req.getMemberId(), req.getAppId());
                if(!roleInGroupOne.isOk()){
                    return roleInGroupOne;
                }
                //获取操作人权限
                ResponseVO<GetRoleInGroupResp> operateRoleInGroupOne = getRoleInGroupOne(req.getGroupId(), req.getOperater(), req.getAppId());
                if(!operateRoleInGroupOne.isOk()){
                    return operateRoleInGroupOne;
                }

                GetRoleInGroupResp data = operateRoleInGroupOne.getData();
                Integer roleInfo = data.getRole();
                boolean isOwner = roleInfo == GroupMemberRoleEnum.OWNER.getCode();
                boolean isManager = roleInfo == GroupMemberRoleEnum.MAMAGER.getCode();

                //不是管理员不能修改权限
                if(req.getRole() != null && !isOwner && !isManager){//req有想修改的权限内容 但操作者不是管理员
                    return ResponseVO.errorResponse(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
                }
                //管理员只有群主能够设置
                if(req.getRole() !=null && req.getRole() == GroupMemberRoleEnum.MAMAGER.getCode()
                && !isOwner){
                    return ResponseVO.errorResponse(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
                }

            }
        }
        ImGroupMemberEntity update = new ImGroupMemberEntity();
        if (StringUtils.isNotBlank(req.getAlias())) {
            update.setAlias(req.getAlias());
        }
        //不能直接修改为群主
        if(req.getRole() != null && req.getRole() != GroupMemberRoleEnum.OWNER.getCode()){
            update.setRole(req.getRole());
        }
        UpdateWrapper<ImGroupMemberEntity> objectUpdateWrapper =new UpdateWrapper<>();
        objectUpdateWrapper.eq("app_id", req.getAppId());
        objectUpdateWrapper.eq("member_id", req.getMemberId());
        objectUpdateWrapper.eq("group_id", req.getGroupId());
        imGroupMemberMapper.update(update,objectUpdateWrapper);

        return ResponseVO.successResponse();
    }


    //批量获取memberId
    @Override
    public List<String> getGroupMemberId(String groupId, Integer appId) {
        return imGroupMemberMapper.getGroupMemberId(appId, groupId);
    }

    //获取管理员的方法
    @Override
    public List<GroupMemberDto> getGroupManager(String groupId, Integer appId) {
        return imGroupMemberMapper.getGroupManager(groupId, appId);
    }


    //禁言群成员
    @Override
    public ResponseVO speak(SpeaMemberReq req) {
        ResponseVO<ImGroupEntity> groupResp = groupService.getGroup(req.getGroupId(), req.getAppId());
        if (!groupResp.isOk()) {
            return groupResp;
        }

        boolean isadmin = false;
        boolean isOwner = false;
        boolean isManager = false;
        GetRoleInGroupResp memberRole = null;
        if (!isadmin) {

            //获取操作人的权限 是管理员or群主or群成员
            ResponseVO<GetRoleInGroupResp> role = getRoleInGroupOne(req.getGroupId(), req.getOperater(), req.getAppId());
            if (!role.isOk()) {
                return role;
            }

            GetRoleInGroupResp data = role.getData();
            Integer roleInfo = data.getRole();

            isOwner = roleInfo == GroupMemberRoleEnum.OWNER.getCode();
            isManager = roleInfo == GroupMemberRoleEnum.MAMAGER.getCode();

            if (!isOwner && !isManager) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
            }

            //获取被操作的权限
            ResponseVO<GetRoleInGroupResp> roleInGroupOne = this.getRoleInGroupOne(req.getGroupId(), req.getMemberId(), req.getAppId());
            if (!roleInGroupOne.isOk()) {
                return roleInGroupOne;
            }
            memberRole = roleInGroupOne.getData();
            //被操作人是群主只能app管理员操作
            if (memberRole.getRole() == GroupMemberRoleEnum.OWNER.getCode()) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_APPMANAGER_ROLE);
            }

            //是管理员并且被操作人不是群成员（是管理员或者群主），无法操作
            if (isManager && memberRole.getRole() != GroupMemberRoleEnum.ORDINARY.getCode()) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
            }
        }

        ImGroupMemberEntity imGroupMemberEntity = new ImGroupMemberEntity();
        if(memberRole == null){
            //获取被操作的权限
            ResponseVO<GetRoleInGroupResp> roleInGroupOne = this.getRoleInGroupOne(req.getGroupId(), req.getMemberId(), req.getAppId());
            if (!roleInGroupOne.isOk()) {
                return roleInGroupOne;
            }
            memberRole = roleInGroupOne.getData();
        }

        imGroupMemberEntity.setGroupMemberId(memberRole.getGroupMemberId());
        if(req.getSpeakDate() > 0){
            imGroupMemberEntity.setSpeakDate(System.currentTimeMillis() + req.getSpeakDate());
        }else{
            imGroupMemberEntity.setSpeakDate(req.getSpeakDate());
        }
        //没有调用数据库的方法来执行更新======================！！！！！！！！！！！！！！！！！！
        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO<Collection<String>> syncMemberJoinedGroup(String operater, Integer appId) {
        return ResponseVO.successResponse(imGroupMemberMapper.syncJoinedGroupId(appId,operater,GroupMemberRoleEnum.LEAVE.getCode()));
    }

}
