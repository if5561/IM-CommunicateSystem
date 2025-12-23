package com.lld.im.service.friendship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lld.im.codec.pack.friendship.ApproverFriendRequestPack;
import com.lld.im.codec.pack.friendship.ReadAllFriendRequestPack;
import com.lld.im.common.Constants.Constants;
import com.lld.im.common.ResponseVO;
import com.lld.im.common.enums.ApproverFriendRequestStatusEnum;
import com.lld.im.common.enums.FriendShipErrorCode;
import com.lld.im.common.enums.command.FriendshipEventCommand;
import com.lld.im.common.exception.ApplicationException;
import com.lld.im.service.friendship.dao.ImFriendShipRequestEntity;
import com.lld.im.service.friendship.dao.mapper.ImFriendShipRequestMapper;
import com.lld.im.service.friendship.model.req.ApproverFriendRequestReq;
import com.lld.im.service.friendship.model.req.FriendDto;
import com.lld.im.service.friendship.model.req.ReadFriendShipRequestReq;
import com.lld.im.service.friendship.service.ImFriendShipRequestService;
import com.lld.im.service.friendship.service.ImFriendService;
import com.lld.im.service.seq.RedisSeq;
import com.lld.im.service.utils.MessageProducer;
import com.lld.im.service.utils.WriteUserSeq;
import org.apache.commons.lang3.StringUtils;
import org.redisson.client.RedisRedirectException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ImFriendShipRequestServiceImpl implements ImFriendShipRequestService {

    @Autowired
    ImFriendShipRequestMapper imFriendShipRequestMapper;

    @Autowired
    private ImFriendService imFriendService;

    @Autowired
    MessageProducer messageProducer;

    @Autowired
    RedisSeq redisSeq;

    @Autowired
    WriteUserSeq writeUserSeq;

    //A + B
    @Override
    public ResponseVO addFriendshipRequest(String fromId, FriendDto dto, Integer appId) {
    //查询有没有重复记录
        QueryWrapper<ImFriendShipRequestEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("app_id",appId);
        queryWrapper.eq("to_id",dto.getToId());
        queryWrapper.eq("from_id",fromId);
        ImFriendShipRequestEntity request = imFriendShipRequestMapper.selectOne(queryWrapper);
        long seq = redisSeq.doGetSeq(appId+":"+
                Constants.SeqConstants.FriendshipRequest);
        if(request==null){ //dto就是传进来的参数对象 包含多个数据
            //todo 插入
            request = new ImFriendShipRequestEntity();
            request.setAddSource(dto.getAddSource());
            request.setAddWording(dto.getAddWording());
            request.setAppId(appId);
            request.setSequence(seq);
            request.setFromId(fromId);
            request.setToId(dto.getToId());
            request.setReadStatus(0);
            request.setApproveStatus(0);
            request.setRemark(dto.getRemark());
            request.setCreateTime(System.currentTimeMillis());
            imFriendShipRequestMapper.insert(request);

        }else{
            //修改记录内容 和更新时间
            if(StringUtils.isNotBlank(dto.getAddSource())){
                //不等于空才更新
                request.setAddSource(dto.getAddSource());
            }
            if(StringUtils.isNotBlank(dto.getRemark())){
                //不等于空才更新
                request.setRemark(dto.getRemark());
            }
            if(StringUtils.isNotBlank(dto.getAddWording())){
                //不等于空才更新
                request.setAddWording(dto.getAddWording());
            }
            request.setSequence(seq);
            imFriendShipRequestMapper.updateById(request);
        }
        //from发送给to toId上线去拉取 因此seq写入toId
        writeUserSeq.writeUserSeq(appId,dto.getToId(),
                Constants.SeqConstants.FriendshipRequest,seq);
        //发送好友申请的tcp给接收方 A+B tcp通知发给B的所有端（clientype imei为空）
        messageProducer.sendToUser(dto.getToId(),
                null, "", FriendshipEventCommand.FRIEND_REQUEST,
                request, appId);
        return ResponseVO.successResponse();
    }


    @Override
    @Transactional
    public ResponseVO approverFriendRequest(ApproverFriendRequestReq req) {
        //好友审批
        ImFriendShipRequestEntity imFriendShipRequestEntity = imFriendShipRequestMapper.selectById(req.getId());
        if (imFriendShipRequestEntity == null) {//请求不存在
            throw new ApplicationException(FriendShipErrorCode.FRIEND_REQUEST_IS_NOT_EXIST);
        }
        if (!req.getOperater().equals(imFriendShipRequestEntity.getToId())) {
            //只能审批发给自己的好友请求 from发送给toId toId要审批自己的好友请求就是与发送过来的toId与操作人operater比较
            throw new ApplicationException(FriendShipErrorCode.NOT_APPROVER_OTHER_MAN_REQUEST);
        }

        long seq = redisSeq.doGetSeq(req.getAppId()+":"+
                Constants.SeqConstants.FriendshipRequest);

        ImFriendShipRequestEntity update = new ImFriendShipRequestEntity();
        update.setApproveStatus(req.getStatus());
        update.setSequence(seq);
        update.setCreateTime(System.currentTimeMillis());
        update.setId(req.getId());
        imFriendShipRequestMapper.updateById(update);

        writeUserSeq.writeUserSeq(req.getAppId(),req.getOperater(),
                Constants.SeqConstants.FriendshipRequest,seq);

        if (ApproverFriendRequestStatusEnum.AGREE.getCode() == req.getStatus()) {
            //同意==》执行添加好友逻辑
            FriendDto dto = new FriendDto();
            dto.setAddSource(imFriendShipRequestEntity.getAddSource());
            dto.setAddWording(imFriendShipRequestEntity.getAddWording());
            dto.setRemark(imFriendShipRequestEntity.getRemark());
            dto.setToId(imFriendShipRequestEntity.getToId());
            ResponseVO responseVO = imFriendService.doAddFriend(req, imFriendShipRequestEntity.getFromId(), dto, req.getAppId());
            if (!responseVO.isOk() && responseVO.getCode() != FriendShipErrorCode.TO_IS_YOUR_FRIEND.getCode()) {
                return responseVO;
            }
        }
        ApproverFriendRequestPack approverFriendRequestPack = new ApproverFriendRequestPack();
        approverFriendRequestPack.setId(req.getId());
        approverFriendRequestPack.setSequence(seq);
        approverFriendRequestPack.setStatus(req.getStatus());
        messageProducer.sendToUser(imFriendShipRequestEntity.getToId(),req.getClientType(),req.getImei(), FriendshipEventCommand
                .FRIEND_REQUEST_APPROVER,approverFriendRequestPack,req.getAppId());
        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO readFriendShipRequestReq(ReadFriendShipRequestReq req) {
        QueryWrapper<ImFriendShipRequestEntity> query = new QueryWrapper<>();
        query.eq("app_id",req.getAppId());
        query.eq("to_id",req.getFromId());//“我要审批掉发给我的好友申请”
        //数据库中的toId字段（A发起好友请求对B）对应要执行已读这个操作人B的fromId
        // req是要执行操作的人B发出的请求
        long seq = redisSeq.doGetSeq(req.getAppId()+":"+
                Constants.SeqConstants.FriendshipRequest);
        ImFriendShipRequestEntity update =new ImFriendShipRequestEntity();
        update.setReadStatus(1);//已读
        update.setSequence(seq);
        imFriendShipRequestMapper.update(update,query);
        writeUserSeq.writeUserSeq(req.getAppId(),req.getOperater(),
                Constants.SeqConstants.FriendshipRequest,seq);
        //TCP通知
        ReadAllFriendRequestPack readAllFriendRequestPack = new ReadAllFriendRequestPack();
        readAllFriendRequestPack.setFromId(req.getFromId());
        readAllFriendRequestPack.setSequence(seq);
        messageProducer.sendToUser(req.getFromId(),req.getClientType(),req.getImei(),FriendshipEventCommand
                .FRIEND_REQUEST_READ,readAllFriendRequestPack,req.getAppId());
        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO getFriendRequest(String fromId, Integer appId) {
        QueryWrapper<ImFriendShipRequestEntity> query = new QueryWrapper<>();
        query.eq("app_id",appId);
        query.eq("to_id",fromId);//一样的逻辑 我要查看所有加我的人的好友申请
        List<ImFriendShipRequestEntity> requestList =imFriendShipRequestMapper.selectList(query);
        return ResponseVO.successResponse(requestList);
    }
}
