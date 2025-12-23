package com.lld.im.service.group.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.lld.im.codec.pack.group.CreateGroupPack;
import com.lld.im.codec.pack.group.DestroyGroupPack;
import com.lld.im.codec.pack.group.UpdateGroupInfoPack;
import com.lld.im.common.Constants.Constants;
import com.lld.im.common.ResponseVO;
import com.lld.im.common.config.AppConfig;
import com.lld.im.common.enums.GroupErrorCode;
import com.lld.im.common.enums.GroupMemberRoleEnum;
import com.lld.im.common.enums.GroupStatusEnum;
import com.lld.im.common.enums.GroupTypeEnum;
import com.lld.im.common.enums.command.GroupEventCommand;
import com.lld.im.common.exception.ApplicationException;
import com.lld.im.common.model.ClientInfo;
import com.lld.im.common.model.SyncReq;
import com.lld.im.common.model.SyncResp;
import com.lld.im.service.group.dao.ImGroupEntity;
import com.lld.im.service.group.dao.mapper.ImGroupMapper;
import com.lld.im.service.group.model.callback.DestroyGroupCallbackDto;
import com.lld.im.service.group.model.req.*;
import com.lld.im.service.group.model.resp.GetGroupResp;
import com.lld.im.service.group.model.resp.GetJoinedGroupResp;
import com.lld.im.service.group.model.resp.GetRoleInGroupResp;
import com.lld.im.service.group.service.ImGroupMemberService;
import com.lld.im.service.group.service.ImGroupService;
import com.lld.im.service.seq.RedisSeq;
import com.lld.im.service.utils.CallbackService;
import com.lld.im.service.utils.GroupMessageProducer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
public class ImGroupServiceImpl implements ImGroupService {

    @Autowired
    ImGroupMapper imGroupDataMapper;

    @Autowired
    ImGroupMemberService groupMemberService;

    @Autowired
    AppConfig appConfig;

    @Autowired
    CallbackService callbackService;

    @Autowired
    GroupMessageProducer groupMessageProducer;

    @Autowired
    RedisSeq redisSeq;

    //导入群组
    @Override
    public ResponseVO importGroup(ImportGroupReq req) {
        //判断群id是否存在
        QueryWrapper<ImGroupEntity> query = new QueryWrapper<>();
        if(StringUtils.isEmpty(req.getGroupId())){
            req.setGroupId(UUID.randomUUID().toString().replace("-", ""));
            //群id为空 默认设置为一个uuid
        }else{
            query.eq("group_id", req.getGroupId());
            query.eq("app_id", req.getAppId());
            Integer integer = imGroupDataMapper.selectCount(query);
            if(integer>0){
                throw new ApplicationException(GroupErrorCode.GROUP_IS_EXIST);
            }
        }
        ImGroupEntity imGroupEntity = new ImGroupEntity();
        if(req.getGroupType()== GroupTypeEnum.PUBLIC.getCode() && StringUtils.isBlank(req.getOwnerId())){
            //公共群必须有群主id
            throw new ApplicationException(GroupErrorCode.PUBLIC_GROUP_MUST_HAVE_OWNER);
        }
        if(req.getCreateTime()==null){
            imGroupEntity.setCreateTime(System.currentTimeMillis());
        }
        imGroupEntity.setStatus(GroupStatusEnum.NORMAL.getCode());
        BeanUtils.copyProperties(req, imGroupEntity);
        imGroupEntity.setAppId(123);
        int insert = imGroupDataMapper.insert(imGroupEntity);
        if (insert != 1) {
            throw new ApplicationException(GroupErrorCode.IMPORT_GROUP_ERROR);
        }
        return ResponseVO.successResponse();
    }

    //查询群是否存在
    @Override
    public ResponseVO<ImGroupEntity> getGroup(String groupId, Integer appId) {

        QueryWrapper<ImGroupEntity> query = new QueryWrapper<>();
        query.eq("app_id", appId);
        query.eq("group_id", groupId);
        ImGroupEntity imGroupEntity = imGroupDataMapper.selectOne(query);

        if (imGroupEntity == null) {
            return ResponseVO.errorResponse(GroupErrorCode.GROUP_IS_NOT_EXIST);
        }
        return ResponseVO.successResponse(imGroupEntity);
    }
    //创建群组
    @Override
    @Transactional
    public ResponseVO createGroup(CreateGroupReq req) {
        boolean isAdmin = false;
        if(!isAdmin){
            req.setOwnerId(req.getOperater());
        }
        //1.判断群id是否存在
        QueryWrapper<ImGroupEntity> query = new QueryWrapper<>();

        if (StringUtils.isEmpty(req.getGroupId())) {
            req.setGroupId(UUID.randomUUID().toString().replace("-", ""));
        } else {
            query.eq("group_id", req.getGroupId());
            query.eq("app_id", req.getAppId());
            Integer integer = imGroupDataMapper.selectCount(query);
            if (integer > 0) {
                throw new ApplicationException(GroupErrorCode.GROUP_IS_EXIST);
            }
        }
        if (req.getGroupType() == GroupTypeEnum.PUBLIC.getCode() && StringUtils.isBlank(req.getOwnerId())) {
            throw new ApplicationException(GroupErrorCode.PUBLIC_GROUP_MUST_HAVE_OWNER);
        }
        ImGroupEntity imGroupEntity =new ImGroupEntity();
        long seq = redisSeq.doGetSeq(req.getAppId() + ":" + Constants.SeqConstants.Group);
        imGroupEntity.setSequence(seq);
        imGroupEntity.setCreateTime(System.currentTimeMillis());
        imGroupEntity.setStatus(GroupStatusEnum.NORMAL.getCode());
        BeanUtils.copyProperties(req,imGroupEntity);
        imGroupEntity.setAppId(123);
        imGroupEntity.setOwnerId("123");
        int insert = imGroupDataMapper.insert(imGroupEntity);

        GroupMemberDto groupMemberDto = new GroupMemberDto();
        groupMemberDto.setMemberId(req.getOwnerId());//这是群主id 先导入群主
        groupMemberDto.setRole(GroupMemberRoleEnum.OWNER.getCode());
        groupMemberDto.setJoinTime(System.currentTimeMillis());
        groupMemberService.addGroupMember(req.getGroupId(),req.getAppId(),groupMemberDto);

        //插入其他群成员
        for(GroupMemberDto dto :req.getMember()){
            groupMemberService.addGroupMember(req.getGroupId(),req.getAppId(),dto);
        }
        //之后
        if(appConfig.isCreateGroupAfterCallback()){
            callbackService.callback(req.getAppId(), Constants.CallbackCommand.CreateGroupAfter,
                    JSONObject.toJSONString(imGroupEntity));
        }
        //tcp通知 pack是通知报文类
        CreateGroupPack createGroupPack = new CreateGroupPack();
        BeanUtils.copyProperties(imGroupEntity, createGroupPack);
        groupMessageProducer.producer(req.getOperater(), GroupEventCommand.CREATED_GROUP, createGroupPack
                , new ClientInfo(req.getAppId(), req.getClientType(), req.getImei()));
        return ResponseVO.successResponse();
    }

    //修改群组
    //    /**
//     * @param [req]
//     * @return com.lld.im.common.ResponseVO
//     * @description 修改群基础信息，如果是后台管理员调用，则不检查权限，如果不是则检查权限，如果是私有群（微信群）任何人都可以修改资料，公开群只有管理员可以修改
//     * 如果是群主或者管理员可以修改其他信息。
//     * @author chackylee
//     */
    @Override
    @Transactional
    public ResponseVO updateBaseGroupInfo(UpdateGroupReq req) {
        //1.判断群id是否存在
        QueryWrapper<ImGroupEntity> query = new QueryWrapper<>();
        query.eq("group_id", req.getGroupId());
        query.eq("app_id", req.getAppId());
        ImGroupEntity imGroupEntity = imGroupDataMapper.selectOne(query);
        if (imGroupEntity == null) {
            throw new ApplicationException(GroupErrorCode.GROUP_IS_NOT_EXIST);
        }

        if(imGroupEntity.getStatus() == GroupStatusEnum.DESTROY.getCode()){
            throw new ApplicationException(GroupErrorCode.GROUP_IS_DESTROY);
        }
        //有关管理员修改的内容 后面会加进来
        boolean isAdmin = false;
        if(!isAdmin){
            //todo 校验权限
            ResponseVO<GetRoleInGroupResp> role = groupMemberService.getRoleInGroupOne(req.getGroupId(), req.getOperater(), req.getAppId());
            if(!role.isOk()){
                return role;
            }
            GetRoleInGroupResp roleData = role.getData();
            int roleInfo = roleData.getRole();//拿出当前成员的权限值

            boolean isManager = roleInfo == GroupMemberRoleEnum.MAMAGER.getCode() ||
                    roleInfo == GroupMemberRoleEnum.OWNER.getCode();//合并一下
            //公开群只能群主修改资料
            if (!isManager && GroupTypeEnum.PUBLIC.getCode() == imGroupEntity.getGroupType()) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
            }

        }

        ImGroupEntity update = new ImGroupEntity();
        long seq = redisSeq.doGetSeq(req.getAppId() + ":" + Constants.SeqConstants.Group);
        BeanUtils.copyProperties(req, update);
        update.setUpdateTime(System.currentTimeMillis());
        update.setSequence(seq);
        int row = imGroupDataMapper.update(update, query);
        if (row != 1) {
            throw new ApplicationException(GroupErrorCode.UPDATE_GROUP_BASE_INFO_ERROR);
        }
        //之后
        if(appConfig.isModifyGroupAfterCallback()){
            callbackService.callback(req.getAppId(),Constants.CallbackCommand.UpdateGroupAfter,
                    JSONObject.toJSONString(imGroupDataMapper.selectOne(query)));
        }   //查出修改以后的群返回给业务服务器

        UpdateGroupInfoPack pack = new UpdateGroupInfoPack();
        BeanUtils.copyProperties(req, pack);
        groupMessageProducer.producer(req.getOperater(), GroupEventCommand.UPDATED_GROUP,
                pack, new ClientInfo(req.getAppId(), req.getClientType(), req.getImei()));
        return ResponseVO.successResponse();
    }

    //获取群组信息 +群内所有成员
    @Override
    public ResponseVO getGroupInfo(GetGroupInfoReq req) {
        //判断群组是不是存在
        ResponseVO group = this.getGroup(req.getGroupId(), req.getAppId());
        if(!group.isOk()){
            return group;
        }

        GetGroupResp getGroupResp = new GetGroupResp();
        BeanUtils.copyProperties(group.getData(), getGroupResp);
        try {
            ResponseVO<List<GroupMemberDto>> groupMember = groupMemberService.getGroupMember(req.getGroupId(), req.getAppId());
            if (groupMember.isOk()) {
                getGroupResp.setMemberList(groupMember.getData());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ResponseVO.successResponse(getGroupResp);
    }

    //查询用户进入的群列表
    @Override
    public ResponseVO getJoinedGroup(GetJoinedGroupReq req) {
        //1.获取到所有加入的群id --根据member获取
        ResponseVO<Collection<String>> memberJoinedGroup = groupMemberService.getMemberJoinedGroup(req);
        if (memberJoinedGroup.isOk()) {
            GetJoinedGroupResp resp = new GetJoinedGroupResp();
            if (CollectionUtils.isEmpty(memberJoinedGroup.getData())) {
                resp.setTotalCount(0);
                resp.setGroupList(new ArrayList<>());//没有加入群 空的
                return ResponseVO.successResponse(resp);
            }
            //2.根据群id获取群信息
            QueryWrapper<ImGroupEntity> query = new QueryWrapper<>();
            query.eq("app_id", req.getAppId());
            query.in("group_id", memberJoinedGroup.getData());//groupId在这个列表之内
            if (CollectionUtils.isNotEmpty(req.getGroupType())) {
                query.in("group_type", req.getGroupType());
            }//要查询进入的哪些群类型
            List<ImGroupEntity> groupList = imGroupDataMapper.selectList(query);
            resp.setGroupList(groupList);
            if(req.getLimit()==null){
                resp.setTotalCount(groupList.size());
            }else{
                resp.setTotalCount(imGroupDataMapper.selectCount(query));
                //这一行和上一行 暂时看没什么区别因为还没加分页条件。。
            }
            return ResponseVO.successResponse(resp);
        }else{//memberJoinedGroup 不OK 直接返回错误值
            return memberJoinedGroup;
        }
    }

    /**
     * @param
     * @return com.lld.im.common.ResponseVO
     * @description 解散群组，只支持后台管理员和群主解散
     * @author chackylee
     */
    //解散群组
    @Override
    @Transactional
    public ResponseVO destroyGroup(DestroyGroupReq req) {
        boolean isAdmin = false;
        QueryWrapper<ImGroupEntity> objectQueryWrapper = new QueryWrapper<>();
        objectQueryWrapper.eq("group_id", req.getGroupId());
        objectQueryWrapper.eq("app_id", req.getAppId());
        ImGroupEntity imGroupEntity =imGroupDataMapper.selectOne(objectQueryWrapper);
        if (imGroupEntity == null) {
            throw new ApplicationException(GroupErrorCode.GROUP_IS_NOT_EXIST);
        }

        if(imGroupEntity.getStatus() == GroupStatusEnum.DESTROY.getCode()){
            throw new ApplicationException(GroupErrorCode.GROUP_IS_DESTROY);
        }
        if (!isAdmin) {//不是管理员
            if (imGroupEntity.getGroupType() == GroupTypeEnum.PRIVATE.getCode()) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_APPMANAGER_ROLE);
            }//私有群只能APPid管理员解散
            if (imGroupEntity.getGroupType()==GroupTypeEnum.PUBLIC.getCode()&&
            !imGroupEntity.getOwnerId().equals(req.getOperater())){
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
            }
        }
        ImGroupEntity update = new ImGroupEntity();
        long seq = redisSeq.doGetSeq(req.getAppId() + ":" + Constants.SeqConstants.Group);
        update.setStatus(GroupStatusEnum.DESTROY.getCode());
        update.setSequence(seq);
        int update1 = imGroupDataMapper.update(update,objectQueryWrapper);
        if(update1!=1){
            throw new ApplicationException(GroupErrorCode.UPDATE_GROUP_BASE_INFO_ERROR);
        }
        //之后
        if(appConfig.isModifyGroupAfterCallback()){
            DestroyGroupCallbackDto dto = new DestroyGroupCallbackDto();
            dto.setGroupId(req.getGroupId());
            callbackService.callback(req.getAppId()
                    ,Constants.CallbackCommand.DestoryGroupAfter,
                    JSONObject.toJSONString(dto));
        }
        DestroyGroupPack pack = new DestroyGroupPack();
        pack.setSequence(seq);
        pack.setGroupId(req.getGroupId());
        groupMessageProducer.producer(req.getOperater(),
                GroupEventCommand.DESTROY_GROUP, pack, new ClientInfo(req.getAppId(), req.getClientType(), req.getImei()));

        return ResponseVO.successResponse();
    }

    //转让群组
    @Override
    @Transactional
    public ResponseVO transferGroup(TransferGroupReq req) {
        //1.先判断操作人是否在群内 再检查是不是群主 还有被操作人是不是在群内
        ResponseVO<GetRoleInGroupResp> roleInGroupOne = groupMemberService.getRoleInGroupOne(req.getGroupId(), req.getOperater(), req.getAppId());
        if(!roleInGroupOne.isOk()){
            return roleInGroupOne;
        }
        if(roleInGroupOne.getData().getRole()!=GroupMemberRoleEnum.OWNER.getCode()){
            return ResponseVO.errorResponse(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
        }
        ResponseVO<GetRoleInGroupResp> newOwnerRole = groupMemberService.getRoleInGroupOne(req.getGroupId(), req.getOwnerId(), req.getAppId());
        //新群主
        if (!newOwnerRole.isOk()) {
            return newOwnerRole;
        }
        QueryWrapper<ImGroupEntity> objectQueryWrapper = new QueryWrapper<>();
        objectQueryWrapper.eq("app_id",req.getAppId());
        objectQueryWrapper.eq("group_id",req.getGroupId());
        ImGroupEntity imGroupEntity = imGroupDataMapper.selectOne(objectQueryWrapper);
        if(imGroupEntity.getStatus()==GroupStatusEnum.DESTROY.getCode()){
            throw new ApplicationException(GroupErrorCode.GROUP_IS_DESTROY);
        }
        ImGroupEntity updateGroup = new ImGroupEntity();
        updateGroup.setOwnerId(req.getOwnerId());
        UpdateWrapper<ImGroupEntity> updateGroupWrapper = new UpdateWrapper<>();
        updateGroupWrapper.eq("app_id", req.getAppId());
        updateGroupWrapper.eq("group_id", req.getGroupId());
        imGroupDataMapper.update(updateGroup,updateGroupWrapper);
        groupMemberService.transferGroupMember(req.getOwnerId(), req.getGroupId(), req.getAppId());
        //这里用 成员表修改一下两个人的信息       Ownerid 新群主的id
        return ResponseVO.successResponse();
    }

    //禁言群
    @Override
    public ResponseVO muteGroup(MuteGroupReq req) {
        ResponseVO<ImGroupEntity> groupResp = getGroup(req.getGroupId(), req.getAppId());
        if (!groupResp.isOk()) {
            return groupResp;
        }
        if(groupResp.getData().getStatus() == GroupStatusEnum.DESTROY.getCode()){
            throw new ApplicationException(GroupErrorCode.GROUP_IS_DESTROY);
        }

        boolean isadmin = false;

        if (!isadmin) {
            //不是后台调用需要检查权限
            ResponseVO<GetRoleInGroupResp> role = groupMemberService.getRoleInGroupOne(req.getGroupId(), req.getOperater(), req.getAppId());

            if (!role.isOk()) {
                return role;
            }

            GetRoleInGroupResp data = role.getData();
            Integer roleInfo = data.getRole();

            boolean isManager = roleInfo == GroupMemberRoleEnum.MAMAGER.getCode() || roleInfo == GroupMemberRoleEnum.OWNER.getCode();

            //公开群只能群主修改资料
            if (!isManager) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
            }
        }

        ImGroupEntity update = new ImGroupEntity();
        update.setMute(req.getMute());

        UpdateWrapper<ImGroupEntity> wrapper = new UpdateWrapper<>();
        wrapper.eq("group_id",req.getGroupId());
        wrapper.eq("app_id",req.getAppId());
        imGroupDataMapper.update(update,wrapper);

        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO syncJoinedGroupList(SyncReq req) {
        if(req.getMaxLimit() > 100){
            req.setMaxLimit(100);
        }

        SyncResp<ImGroupEntity> resp = new SyncResp<>();

        ResponseVO<Collection<String>> memberJoinedGroup = groupMemberService.syncMemberJoinedGroup(req.getOperater(), req.getAppId());
        if(memberJoinedGroup.isOk()){

            Collection<String> data = memberJoinedGroup.getData();
            QueryWrapper<ImGroupEntity> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("app_id",req.getAppId());
            queryWrapper.in("group_id",data);
            queryWrapper.gt("sequence",req.getLastSequence());
            queryWrapper.last(" limit " + req.getMaxLimit());
            queryWrapper.orderByAsc("sequence");

            List<ImGroupEntity> list = imGroupDataMapper.selectList(queryWrapper);

            if(!CollectionUtils.isEmpty(list)){
                ImGroupEntity maxSeqEntity
                        = list.get(list.size() - 1);
                resp.setDataList(list);
                //设置最大seq
                Long maxSeq =
                        imGroupDataMapper.getGroupMaxSeq(data, req.getAppId());
                resp.setMaxSequence(maxSeq);
                //设置是否拉取完毕        查询到的seq >= 数据库中已写入存在的最大seq 就拉取完毕！
                resp.setCompleted(maxSeqEntity.getSequence() >= maxSeq);
                return ResponseVO.successResponse(resp);
            }

        }
        resp.setCompleted(true);
        return ResponseVO.successResponse(resp);
    }

    //获取用户加入群组的最大seq
    @Override
    public Long getUserGroupMaxSeq(String userId, Integer appId) {
        ResponseVO<Collection<String>> memberJoinedGroup = groupMemberService.syncMemberJoinedGroup(userId, appId);
        if(!memberJoinedGroup.isOk()){
            throw new ApplicationException(500,"");
        }
        Long maxSeq =
                imGroupDataMapper.getGroupMaxSeq(memberJoinedGroup.getData(),
                        appId);
        return maxSeq;
    }
}
