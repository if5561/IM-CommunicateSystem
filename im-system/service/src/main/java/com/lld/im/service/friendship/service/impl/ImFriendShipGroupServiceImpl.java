package com.lld.im.service.friendship.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lld.im.codec.pack.friendship.AddFriendGroupPack;
import com.lld.im.codec.pack.friendship.DeleteFriendGroupPack;
import com.lld.im.common.Constants.Constants;
import com.lld.im.common.ResponseVO;
import com.lld.im.common.enums.DelFlagEnum;
import com.lld.im.common.enums.FriendShipErrorCode;
import com.lld.im.common.enums.command.FriendshipEventCommand;
import com.lld.im.common.model.ClientInfo;
import com.lld.im.service.friendship.dao.ImFriendShipGroupEntity;
import com.lld.im.service.friendship.dao.mapper.ImFriendShipGroupMapper;
import com.lld.im.service.friendship.model.req.AddFriendShipGroupMemberReq;
import com.lld.im.service.friendship.model.req.AddFriendShipGroupReq;
import com.lld.im.service.friendship.model.req.DeleteFriendShipGroupReq;
import com.lld.im.service.friendship.service.ImFriendShipGroupMemberService;
import com.lld.im.service.friendship.service.ImFriendShipGroupService;
import com.lld.im.service.seq.RedisSeq;
import com.lld.im.service.utils.MessageProducer;
import com.lld.im.service.utils.WriteUserSeq;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.DuplicateFormatFlagsException;

@Service
public class ImFriendShipGroupServiceImpl implements ImFriendShipGroupService {

    @Autowired
    ImFriendShipGroupMapper imFriendShipGroupMapper;

    @Autowired
    ImFriendShipGroupMemberService imFriendShipGroupMemberService;

    @Autowired
    MessageProducer messageProducer;

    @Autowired
    RedisSeq redisSeq;

    @Autowired
    WriteUserSeq writeUserSeq;

    //添加分组
    @Override
    @Transactional
    public ResponseVO addGroup(AddFriendShipGroupReq req) {
        QueryWrapper<ImFriendShipGroupEntity> query = new QueryWrapper<>();
        query.eq("app_id",req.getAppId());
        query.eq("from_id",req.getFromId());
        query.eq("group_name",req.getGroupName());
        query.eq("del_flag", DelFlagEnum.NORMAL.getCode());

        ImFriendShipGroupEntity entity = imFriendShipGroupMapper.selectOne(query);
        if (entity != null) {
            return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_SHIP_GROUP_IS_EXIST);
        }//已存在
        //存在且已删除 修改为正常状态
        if(entity.getDelFlag()==DelFlagEnum.DELETE.getCode()){
            entity.setDelFlag(DelFlagEnum.NORMAL.getCode());
        }

        ImFriendShipGroupEntity insert = new ImFriendShipGroupEntity();
        //补全数据库字段
        insert.setAppId(req.getAppId());
        insert.setCreateTime(System.currentTimeMillis());
        insert.setDelFlag(DelFlagEnum.NORMAL.getCode());
        insert.setGroupName(req.getGroupName());
        long seq = redisSeq.doGetSeq(req.getAppId() + ":" + Constants.SeqConstants.FriendshipGroup);
        insert.setSequence(seq);
        insert.setFromId(req.getFromId());
        try{
            int insert1 = imFriendShipGroupMapper.insert(insert);
            if(insert1!=1){
                return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_SHIP_GROUP_CREATE_ERROR);
            }
            if(insert1 ==1 && CollectionUtil.isNotEmpty(req.getToIds())){
                //todo 直接将用户添加到组内
                AddFriendShipGroupMemberReq addFriendShipGroupMemberReq = new AddFriendShipGroupMemberReq();
                addFriendShipGroupMemberReq.setFromId(req.getFromId());
                addFriendShipGroupMemberReq.setGroupName(req.getGroupName());
                addFriendShipGroupMemberReq.setToIds(req.getToIds());
                addFriendShipGroupMemberReq.setAppId(req.getAppId());
                imFriendShipGroupMemberService.addGroupMember(addFriendShipGroupMemberReq);
                return ResponseVO.successResponse();
            }
        }catch (DuplicateKeyException e){//数据库表内数据重复了
            e.getStackTrace();
            return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_SHIP_GROUP_IS_EXIST);
        }
        AddFriendGroupPack addFriendGropPack = new AddFriendGroupPack();
        addFriendGropPack.setFromId(req.getFromId());
        addFriendGropPack.setGroupName(req.getGroupName());
        addFriendGropPack.setSequence(seq);
        messageProducer.sendToUserExceptClient(req.getFromId(), FriendshipEventCommand.FRIEND_GROUP_ADD,
                addFriendGropPack,new ClientInfo(req.getAppId(),req.getClientType(),req.getImei()));

        //写入seq
        writeUserSeq.writeUserSeq(req.getAppId(), req.getFromId(), Constants.SeqConstants.FriendshipGroup, seq);
        return ResponseVO.successResponse();
    }



    //删除分组
    @Override
    @Transactional
    public ResponseVO deleteGroup(DeleteFriendShipGroupReq req) {
        for(String groupName:req.getGroupName()){
            QueryWrapper<ImFriendShipGroupEntity> query = new QueryWrapper<>();
            query.eq("group_name", groupName);
            query.eq("app_id", req.getAppId());
            query.eq("from_id", req.getFromId());
            query.eq("del_flag", DelFlagEnum.NORMAL.getCode());
            //循环 一个个删除
            ImFriendShipGroupEntity entity =imFriendShipGroupMapper.selectOne(query);
            if(entity!=null){
                long seq = redisSeq.doGetSeq(req.getAppId() + ":" + Constants.SeqConstants.FriendshipGroup);
                ImFriendShipGroupEntity update = new ImFriendShipGroupEntity();
                update.setSequence(seq);
                update.setGroupId(entity.getGroupId());//entity是从数据库中查询出来的要被删除的分组的完整信息对象
                update.setDelFlag(DelFlagEnum.DELETE.getCode());
                imFriendShipGroupMapper.updateById(update);//update只是“更新请求体”。只包含了我们想要修改的字段及其新值
                imFriendShipGroupMemberService.clearGroupMember(entity.getGroupId());//顺便清除组内好友
                DeleteFriendGroupPack deleteFriendGroupPack = new DeleteFriendGroupPack();
                deleteFriendGroupPack.setFromId(req.getFromId());
                deleteFriendGroupPack.setGroupName(groupName);
                deleteFriendGroupPack.setSequence(seq);
                //TCP通知
                messageProducer.sendToUserExceptClient(req.getFromId(), FriendshipEventCommand.FRIEND_GROUP_DELETE,
                        deleteFriendGroupPack,new ClientInfo(req.getAppId(),req.getClientType(),req.getImei()));
                //写入seq
                writeUserSeq.writeUserSeq(req.getAppId(), req.getFromId(), Constants.SeqConstants.FriendshipGroup, seq);
            }
        }
        return ResponseVO.successResponse();
    }

    //查询分组
    @Override
    public ResponseVO getGroup(String fromId, String groupName, Integer appId) {
        QueryWrapper<ImFriendShipGroupEntity> query = new QueryWrapper<>();
        query.eq("group_name", groupName);
        query.eq("app_id", appId);
        query.eq("from_id", fromId);
        query.eq("del_flag", DelFlagEnum.NORMAL.getCode());

        ImFriendShipGroupEntity entity = imFriendShipGroupMapper.selectOne(query);
        if (entity == null) {
            return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_SHIP_GROUP_IS_NOT_EXIST);
        }
        return ResponseVO.successResponse(entity);
    }


}
