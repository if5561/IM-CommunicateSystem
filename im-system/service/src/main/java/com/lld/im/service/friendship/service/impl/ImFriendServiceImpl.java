package com.lld.im.service.friendship.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lld.im.codec.pack.friendship.*;
import com.lld.im.common.Constants.Constants;
import com.lld.im.common.ResponseVO;
import com.lld.im.common.config.AppConfig;
import com.lld.im.common.enums.*;
import com.lld.im.common.enums.command.FriendshipEventCommand;
import com.lld.im.common.exception.ApplicationException;
import com.lld.im.common.model.RequestBase;
import com.lld.im.common.model.SyncReq;
import com.lld.im.common.model.SyncResp;
import com.lld.im.service.friendship.dao.ImFriendShipEntity;
import com.lld.im.service.friendship.dao.mapper.ImFriendShipMapper;
import com.lld.im.service.friendship.model.callback.AddFriendAfterCallbackDto;
import com.lld.im.service.friendship.model.callback.AddFriendBlackAfterCallbackDto;
import com.lld.im.service.friendship.model.callback.DeleteFriendAfterCallbackDto;
import com.lld.im.service.friendship.model.req.*;
import com.lld.im.service.friendship.model.resp.CheckFriendShipResp;
import com.lld.im.service.friendship.model.resp.ImportFriendShipResp;
import com.lld.im.service.friendship.service.ImFriendShipRequestService;
import com.lld.im.service.friendship.service.ImFriendService;
import com.lld.im.service.seq.RedisSeq;
import com.lld.im.service.user.dao.ImUserDataEntity;
import com.lld.im.service.user.service.ImUserService;
import com.lld.im.service.utils.CallbackService;
import com.lld.im.service.utils.MessageProducer;
import com.lld.im.service.utils.WriteUserSeq;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ImFriendServiceImpl implements ImFriendService {

    @Autowired
    ImFriendShipMapper imFriendShipMapper;

    @Autowired
    ImUserService imUserService;

    @Autowired
    AppConfig appConfig;

    @Autowired
    CallbackService callbackService;

    @Autowired
    ImFriendShipRequestService imFriendShipRequestService;

    @Autowired
    MessageProducer messageProducer;

    @Autowired
    RedisSeq redisSeq;

    @Autowired
    WriteUserSeq writeUserSeq;

    //导入关系链的开发
    @Override
    public ResponseVO importFriendShip(ImportFriendShipReq req) {
        if (req.getFriendItem().size() > 100) {
            return ResponseVO.errorResponse(FriendShipErrorCode.IMPORT_SIZE_BEYOND);
        }

        ImportFriendShipResp resp = new ImportFriendShipResp();
        List<String> successId = new ArrayList<>();
        List<String> errorId = new ArrayList<>();

        for (ImportFriendShipReq.ImportFriendDto dto :
                req.getFriendItem()) {

            ImFriendShipEntity entity = new ImFriendShipEntity();//创建一个实体对象
            BeanUtils.copyProperties(dto, entity);//dto的属性赋值给当前对象
            entity.setAppId(req.getAppId());//填充其他数据
            entity.setFromId(req.getFromId());
            try {
                int insert = imFriendShipMapper.insert(entity);
                if (insert == 1) {
                    successId.add(dto.getToId());
                } else {
                    errorId.add(dto.getToId());
                }
            } catch (Exception e) {
                e.printStackTrace();
                errorId.add(dto.getToId());
            }
        }
        resp.setSuccessId(successId);
        resp.setErrorId(errorId);
        return ResponseVO.successResponse(resp);
    }


    /**
     * 添加好友
     *
     * @param req
     * @return
     */
    @Override
    public ResponseVO addFriend(AddFriendReq req) {
        //1.判断用户是否存在
        ResponseVO<ImUserDataEntity> fromInfo = imUserService.getSingleUserInfo(req.getFromId(), req.getAppId());
        if (!fromInfo.isOk()) {
            return fromInfo;//fromId不存在 直接返回
        }
        ResponseVO<ImUserDataEntity> toInfo = imUserService.getSingleUserInfo(req.getToItem().getToId(), req.getAppId());
        if (!toInfo.isOk()) {
            return toInfo;//toId不存在 直接返回
        }

        //之前回调
        if(appConfig.isAddFriendBeforeCallback()){
            ResponseVO callbackResp = callbackService.beforeCallback(req.getAppId(),
                    Constants.CallbackCommand.AddFriendBefore,
                    JSONObject.toJSONString(req));
            if (!callbackResp.isOk()) {
                return callbackResp;
            }
        }

        ImUserDataEntity data = toInfo.getData();//获取 “被添加方（toId）” 的用户详情数据

        if(data.getFriendAllowType()!=null&&data.getFriendAllowType()== AllowFriendTypeEnum.NOT_NEED.getCode()){
            //无需验证
            return this.doAddFriend(req, req.getFromId(), req.getToItem(), req.getAppId());
        }else{
            QueryWrapper<ImFriendShipEntity> query = new QueryWrapper<>();
            query.eq("app_id", req.getAppId());
            query.eq("from_id", req.getFromId());
            query.eq("to_id", req.getToItem().getToId());
            ImFriendShipEntity fromItem = imFriendShipMapper.selectOne(query);
            if (fromItem == null || fromItem.getStatus()
                    != FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode()) {
                //插入一条好友申请的数据
                ResponseVO responseVO = imFriendShipRequestService.addFriendshipRequest(req.getFromId(), req.getToItem(), req.getAppId());
                if (!responseVO.isOk()) {
                    return responseVO;
                }
            }else {
                return ResponseVO.errorResponse(FriendShipErrorCode.TO_IS_YOUR_FRIEND);
            }
        }
        return ResponseVO.successResponse();
    }

    //添加好友方法
    @Transactional
    public ResponseVO doAddFriend(RequestBase requestBase,String fromId, FriendDto dto, Integer appId) {
        //A-B
        //Friend表插入 A和B两条记录 ---暂时只插入了A表
        //查询表中是否有记录存在，如果存在则判断状态，如果是已添加 则提示已添加 如果是未添加，则修改状态
        QueryWrapper<ImFriendShipEntity> query = new QueryWrapper<>();
        query.eq("app_id", appId);
        query.eq("from_id", fromId);
        query.eq("to_id", dto.getToId());
        ImFriendShipEntity fromItem = imFriendShipMapper.selectOne(query);
        long seq = 0L;
        if (fromItem == null) {
            //不存在记录 走添加逻辑
            fromItem = new ImFriendShipEntity();
            seq = redisSeq.doGetSeq(appId +":" + Constants.SeqConstants.Friendship);
            fromItem.setAppId(appId);//
            fromItem.setFriendSequence(seq);
            fromItem.setFromId(fromId);
            BeanUtils.copyProperties(dto, fromItem);//dto的数据全部赋值到formItem
            fromItem.setStatus(FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode());
            fromItem.setCreateTime(System.currentTimeMillis());
            int insert = imFriendShipMapper.insert(fromItem);
            if (insert != 1) {
                return ResponseVO.errorResponse(FriendShipErrorCode.ADD_FRIEND_ERROR);
            }
            ///  记录该用户的 seq 走到了哪里
            writeUserSeq.writeUserSeq(appId,fromId,Constants.SeqConstants.Friendship,seq);
        } else {
            //存在 如果存在则判断状态，如果是已添加 则提示已添加 如果是未添加，则修改状态
            if (fromItem.getStatus() == FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode()) {
                return ResponseVO.errorResponse(FriendShipErrorCode.TO_IS_YOUR_FRIEND);
                //已是你的好友
            }
            else { //不是好友 是已删除状态 执行更新操作
                ImFriendShipEntity update = new ImFriendShipEntity();

                if (StringUtils.isNotBlank(dto.getAddSource())) {//不等于空
                    update.setAddSource(dto.getAddSource());
                }

                if (StringUtils.isNotBlank(dto.getRemark())) {//不等于空
                    update.setRemark(dto.getRemark());
                }

                if (StringUtils.isNotBlank(dto.getExtra())) {//不等于空
                    update.setExtra(dto.getExtra());
                }
                seq = redisSeq.doGetSeq(appId +":" + Constants.SeqConstants.Friendship);
                update.setFriendSequence(seq);
                //重新设置为添加状态
                update.setStatus(FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode());
                int result = imFriendShipMapper.update(update, query);//query是where条件的合集，上面的都是set语句
                if (result != 1) {
                    return ResponseVO.errorResponse(FriendShipErrorCode.ADD_FRIEND_ERROR);
                }
                ///  记录该用户的 seq 走到了哪里
                writeUserSeq.writeUserSeq(appId,fromId,Constants.SeqConstants.Friendship,seq);
            }
        }
        //补充：往B表插入好友数据
        QueryWrapper<ImFriendShipEntity> toQuery = new QueryWrapper<>();
        toQuery.eq("app_id", appId);
        toQuery.eq("from_id", dto.getToId());
        toQuery.eq("to_id", fromId);
        ImFriendShipEntity toItem = imFriendShipMapper.selectOne(toQuery);
        if(toItem==null){
            //不存在记录 走添加逻辑
            //seq = redisSeq.doGetSeq(appId +":" + Constants.SeqConstants.Friendship);
            toItem = new ImFriendShipEntity();
            toItem.setAppId(appId);//
            toItem.setFromId(dto.getToId());
            toItem.setToId(fromId);
            toItem.setFriendSequence(seq);
            BeanUtils.copyProperties(dto, toItem);//dto的数据全部赋值到toItem
            toItem.setStatus(FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode());
            toItem.setCreateTime(System.currentTimeMillis());
            toItem.setBlack(FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode());
            int insert = imFriendShipMapper.insert(toItem);
            ///  记录该用户的 seq 走到了哪里
            writeUserSeq.writeUserSeq(appId, dto.getToId(), Constants.SeqConstants.Friendship,seq);
        }else {
            if (FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode() !=
                    toItem.getStatus()) {
                ImFriendShipEntity update = new ImFriendShipEntity();
                //seq = redisSeq.doGetSeq(appId +":" + Constants.SeqConstants.Friendship);
                update.setFriendSequence(seq);
                update.setStatus(FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode());
                imFriendShipMapper.update(update, toQuery);
                writeUserSeq.writeUserSeq(appId, dto.getToId(), Constants.SeqConstants.Friendship, seq);
            }
        }

        //todo 发送tcp 通知 发送方和接收方 A+B 发送给A的其他端 B的所有端
        //发送给from
        AddFriendPack addFriendPack = new AddFriendPack();
        BeanUtils.copyProperties(fromItem,addFriendPack);//赋值
        addFriendPack.setSequence(seq);
        if(requestBase!=null){
            messageProducer.sendToUser(fromId, requestBase.getClientType(),
                    requestBase.getImei(), FriendshipEventCommand.FRIEND_ADD, addFriendPack
                    , requestBase.getAppId());
        }else {
            messageProducer.sendToUser(fromId,
                    FriendshipEventCommand.FRIEND_ADD, addFriendPack
                    , requestBase.getAppId());
        }//发给所有端 admin调用

        //发送给to 所有端
        AddFriendPack addFriendToPack = new AddFriendPack();
        BeanUtils.copyProperties(toItem, addFriendPack);
        messageProducer.sendToUser(toItem.getFromId(),
                FriendshipEventCommand.FRIEND_ADD, addFriendToPack
                , requestBase.getAppId());



        //之后回调  告诉业务服务器 fromId是谁 toitem（被加好友的信息类..备注。。）是什么 用实体类接收一下
        if (appConfig.isAddFriendAfterCallback()) {
            AddFriendAfterCallbackDto callbackDto = new AddFriendAfterCallbackDto();
            callbackDto.setFromId(fromId);
            callbackDto.setToItem(dto);
            callbackService.callback(appId,
                    Constants.CallbackCommand.AddFriendAfter, JSONObject
                            .toJSONString(callbackDto));
        }

        return ResponseVO.successResponse();
    }

    /**
     * 修改好友
     * @param req
     * @return
     */
    @Override
    public ResponseVO updateFriend(UpdateFriendReq req) {
        //1.判断用户是否存在
        ResponseVO<ImUserDataEntity> fromInfo = imUserService.getSingleUserInfo(req.getFromId(), req.getAppId());
        if (!fromInfo.isOk()) {
            return fromInfo;//fromid不存在 直接返回
        }
        ResponseVO<ImUserDataEntity> toInfo = imUserService.getSingleUserInfo(req.getToItem().getToId(), req.getAppId());
        if (!toInfo.isOk()) {
            return toInfo;//toid不存在 直接返回
        }
        ResponseVO responseVO = this.doUpdate(req.getFromId(), req.getToItem(), req.getAppId());
        if (responseVO.isOk()) {//依旧把数据包发送给客户端
            UpdateFriendPack updateFriendPack = new UpdateFriendPack();
            updateFriendPack.setRemark(req.getToItem().getRemark());
            updateFriendPack.setToId(req.getToItem().getToId());
            messageProducer.sendToUser(req.getFromId(),
                    req.getClientType(), req.getImei(), FriendshipEventCommand
                            .FRIEND_UPDATE, updateFriendPack, req.getAppId());
            //之后回调
            if (appConfig.isModifyFriendAfterCallback()) {
                AddFriendAfterCallbackDto callbackDto = new AddFriendAfterCallbackDto();
                callbackDto.setFromId(req.getFromId());
                callbackDto.setToItem(req.getToItem());
                callbackService.callback(req.getAppId(),
                        Constants.CallbackCommand.UpdateFriendAfter, JSONObject
                                .toJSONString(callbackDto));
            }
        }
        return responseVO;
    }

    //修改好友 方法
    @Transactional
    public ResponseVO doUpdate(String fromId, FriendDto dto, Integer appId){

        long seq = redisSeq.doGetSeq(appId + ":" + Constants.SeqConstants.Friendship);
        UpdateWrapper<ImFriendShipEntity> updateWrapper = new UpdateWrapper<>();
        updateWrapper.lambda().set(ImFriendShipEntity::getAddSource,dto.getAddSource())
                .set(ImFriendShipEntity::getExtra,dto.getExtra())
                .set(ImFriendShipEntity::getRemark,dto.getRemark())
                .set(ImFriendShipEntity::getFriendSequence,seq )
                .eq(ImFriendShipEntity::getAppId,appId)
                .eq(ImFriendShipEntity::getFromId,fromId)
                .eq(ImFriendShipEntity::getToId,dto.getToId());
        int update = imFriendShipMapper.update(null, updateWrapper);
        if (update == 1) {
            //  之后回调
            if (appConfig.isModifyFriendAfterCallback()) {
                AddFriendAfterCallbackDto callbackDto = new AddFriendAfterCallbackDto();
                callbackDto.setFromId(fromId);
                callbackDto.setToItem(dto);
                callbackService.callback(appId,
                        Constants.CallbackCommand.UpdateFriendAfter, JSONObject
                                .toJSONString(callbackDto));
            }
        writeUserSeq.writeUserSeq(appId,fromId,Constants.SeqConstants.Friendship,seq);
            return ResponseVO.successResponse();
        }
        return ResponseVO.errorResponse();
    }

    //删除好友
    @Override
    public ResponseVO deleteFriend(DeleteFriendReq req) {
        //先查询是不是好友关系
        QueryWrapper<ImFriendShipEntity> query = new QueryWrapper<>();
        query.eq("app_id", req.getAppId());
        query.eq("from_id", req.getFromId());
        query.eq("to_id", req.getToId());
        ImFriendShipEntity forItem = imFriendShipMapper.selectOne(query);
        if(forItem==null){
            //todo 返回不是好友
            return ResponseVO.errorResponse(FriendShipErrorCode.TO_IS_NOT_YOUR_FRIEND);
        }else{
            //存在关系链数据
            if(forItem.getStatus()==FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode()){
                //必须是好友才能删除
                //删除
                ImFriendShipEntity update = new ImFriendShipEntity();
                long seq = redisSeq.doGetSeq(req.getAppId() + ":" + Constants.SeqConstants.Friendship);
                update.setFriendSequence(seq);
                update.setStatus(FriendShipStatusEnum.FRIEND_STATUS_DELETE.getCode());
                imFriendShipMapper.update(update,query);
                //SET status = 2
                //WHERE (app_id = ? AND from_id = ? AND to_id = ?)
                writeUserSeq.writeUserSeq(req.getAppId(),req.getFromId(),Constants.SeqConstants.Friendship,seq);
                DeleteFriendPack deleteFriendPack = new DeleteFriendPack();
                deleteFriendPack.setFromId(req.getFromId());
                deleteFriendPack.setSequence(seq);
                deleteFriendPack.setToId(req.getToId());
                messageProducer.sendToUser(req.getFromId(),
                        req.getClientType(), req.getImei(),
                        FriendshipEventCommand.FRIEND_DELETE,
                        deleteFriendPack, req.getAppId());

                //之后回调
                if (appConfig.isAddFriendAfterCallback()) {
                    DeleteFriendAfterCallbackDto callbackDto = new DeleteFriendAfterCallbackDto();
                    callbackDto.setFromId(req.getFromId());
                    callbackDto.setToId(req.getToId());
                    callbackService.callback(req.getAppId(),
                            Constants.CallbackCommand.DeleteFriendAfter, JSONObject
                                    .toJSONString(callbackDto));
                }

            }else{
                //已被删除
            return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_IS_DELETED);
            }
        }
        return ResponseVO.successResponse();
    }

    //删除all好友
    @Override
    public ResponseVO deleteAllFriend(DeleteFriendReq req) {
        //先查询是不是好友关系
        QueryWrapper<ImFriendShipEntity> query = new QueryWrapper<>();
        query.eq("app_id", req.getAppId());
        query.eq("from_id", req.getFromId());
        query.eq("status",FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode());
        ImFriendShipEntity update =new ImFriendShipEntity();
        update.setStatus(FriendShipStatusEnum.FRIEND_STATUS_DELETE.getCode());
        imFriendShipMapper.update(update,query);//逻辑删除 执行更新
        //SET status = 2
        //WHERE (app_id = ? AND from_id = ?  AND status = 1)

        DeleteAllFriendPack deleteFriendPack = new DeleteAllFriendPack();
        deleteFriendPack.setFromId(req.getFromId());
        messageProducer.sendToUser(req.getFromId(), req.getClientType(), req.getImei(), FriendshipEventCommand.FRIEND_ALL_DELETE,
                deleteFriendPack, req.getAppId());

        return ResponseVO.successResponse();
    }

    //获取所有好友
    @Override
    public ResponseVO getAllFriendShip(GetAllFriendShipReq req) {
        QueryWrapper<ImFriendShipEntity> query = new QueryWrapper<>();
        query.eq("app_id", req.getAppId());
        query.eq("from_id", req.getFromId());
        return ResponseVO.successResponse(imFriendShipMapper.selectList(query));
    }

    //获取指定好友
    @Override
    public ResponseVO getRelation(GetRelationReq req) {
        QueryWrapper<ImFriendShipEntity> query = new QueryWrapper<>();
        query.eq("app_id", req.getAppId());
        query.eq("from_id", req.getFromId());
        query.eq("to_id",req.getToId());
        ImFriendShipEntity entity = imFriendShipMapper.selectOne(query);
        //query就是查询条件的合集
        if(entity==null){
            //todo 返回记录不存在
            return ResponseVO.errorResponse(FriendShipErrorCode.REPEATSHIP_IS_NOT_EXIST);
        }
        return ResponseVO.successResponse(entity);//存在直接返回就好
    }

    //校验好友
    @Override
    public ResponseVO checkFriendShip(CheckFriendShipReq req) {
        //toId改成一个map key是toid的值 value是0
        Map<String,Integer> result
                =req.getToIds().stream()
                .collect(Collectors.toMap(Function.identity(),s->0));

        List<CheckFriendShipResp> resp = new ArrayList<>();//--
        //判断校验方式 单向 双向
        if(req.getCheckType()== CheckFriendShipTypeEnum.SINGLE.getType()){
            resp = imFriendShipMapper.checkFriendShip(req);
        }else{
            resp = imFriendShipMapper.checkFriendShipBoth(req);
        }

        //resp转成map，，key是toId，value是status
        Map<String,Integer> collect
                =resp.stream()
                .collect(Collectors.toMap(CheckFriendShipResp::getToId
                        ,CheckFriendShipResp::getStatus));
        for (String toId:result.keySet()){
            if(!collect.containsKey(toId)){
                CheckFriendShipResp checkFriendShipResp = new CheckFriendShipResp();
                checkFriendShipResp.setFromId(req.getFromId());
                checkFriendShipResp.setToId(toId);
                checkFriendShipResp.setStatus(result.get(toId));//get（toId）得到value-->0
                resp.add(checkFriendShipResp);
            }
        }
        return ResponseVO.successResponse(resp);
    }

    //添加黑名单 和添加好友逻辑类似
    @Override
    public ResponseVO addBlack(AddFriendShipBlackReq req) {
        //1.判断用户是否存在
        ResponseVO<ImUserDataEntity> fromInfo = imUserService.getSingleUserInfo(req.getFromId(), req.getAppId());
        if (!fromInfo.isOk()) {
            return fromInfo;//fromid不存在 直接返回
        }
        ResponseVO<ImUserDataEntity> toInfo = imUserService.getSingleUserInfo(req.getToId(), req.getAppId());
        if (!toInfo.isOk()) {
            return toInfo;//toid不存在 直接返回
        }
        QueryWrapper<ImFriendShipEntity> query = new QueryWrapper<>();
        query.eq("app_id", req.getAppId());
        query.eq("from_id", req.getFromId());
        query.eq("to_id", req.getToId());
        //单项拉黑 凭借fromid和toid来找到具体是谁拉黑了谁。
        ImFriendShipEntity fromItem = imFriendShipMapper.selectOne(query);
        Long seq = 0L;
        if (fromItem == null) {
            //不存在记录 走添加逻辑
            seq = redisSeq.doGetSeq(req.getAppId() + ":" + Constants.SeqConstants.Friendship);
            fromItem.setFromId(req.getFromId());
            fromItem.setToId(req.getToId());
            fromItem.setFriendSequence(seq);
            fromItem.setAppId(req.getAppId());
            fromItem.setBlack(FriendShipStatusEnum.BLACK_STATUS_BLACKED.getCode());
            fromItem.setCreateTime(System.currentTimeMillis());
            int insert = imFriendShipMapper.insert(fromItem);
            if (insert != 1) {
                return ResponseVO.errorResponse(FriendShipErrorCode.ADD_BLACK_ERROR);
            }
            writeUserSeq.writeUserSeq(req.getAppId(),req.getFromId(),Constants.SeqConstants.Friendship,seq);
        } else {
            //存在 如果存在则判断状态，如果是已经拉黑 则提示已拉黑 如果是未拉黑，则修改状态
            if (fromItem.getBlack() != null &&fromItem.getBlack() == FriendShipStatusEnum.BLACK_STATUS_BLACKED.getCode()) {
                return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_IS_BLACK);
                //已被拉黑
            }
            else { //还没拉黑 执行更新操作
                seq = redisSeq.doGetSeq(req.getAppId() + ":" + Constants.SeqConstants.Friendship);
                ImFriendShipEntity update = new ImFriendShipEntity();
                update.setFriendSequence(seq);
                update.setBlack(FriendShipStatusEnum.BLACK_STATUS_BLACKED.getCode());
                int result = imFriendShipMapper.update(update, query);//query是where条件的合集
                if(result != 1){
                    return ResponseVO.errorResponse(FriendShipErrorCode.ADD_BLACK_ERROR);
                }
                writeUserSeq.writeUserSeq(req.getAppId(),req.getFromId(),Constants.SeqConstants.Friendship,seq);
            }
        }

        AddFriendBlackPack addFriendBlackPack = new AddFriendBlackPack();
        addFriendBlackPack.setFromId(req.getFromId());
        addFriendBlackPack.setSequence(seq);
        addFriendBlackPack.setToId(req.getToId());
        //发送tcp通知
        messageProducer.sendToUser(req.getFromId(), req.getClientType(), req.getImei(),
                FriendshipEventCommand.FRIEND_BLACK_ADD, addFriendBlackPack, req.getAppId());

        //之后回调
        if (appConfig.isAddFriendShipBlackAfterCallback()) {
            AddFriendBlackAfterCallbackDto callbackDto = new AddFriendBlackAfterCallbackDto();
            callbackDto.setFromId(req.getFromId());
            callbackDto.setToId(req.getToId());
            callbackService.callback(req.getAppId(),
                    Constants.CallbackCommand.AddBlackAfter, JSONObject
                            .toJSONString(callbackDto));
        }
        return ResponseVO.successResponse();
    }

    //删除黑名单
    @Override
    public ResponseVO deleteBlack(DeleteBlackReq req) {
        //先查询是不是拉黑关系
        QueryWrapper<ImFriendShipEntity> query = new QueryWrapper<>();
        query.eq("app_id", req.getAppId());
        query.eq("from_id", req.getFromId());
        query.eq("to_id", req.getToId());
        ImFriendShipEntity formItem = imFriendShipMapper.selectOne(query);
        if (formItem.getBlack() != null && formItem.getBlack() == FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode()) {
            throw new ApplicationException(FriendShipErrorCode.FRIEND_IS_NOT_YOUR_BLACK);
        }
        long seq = redisSeq.doGetSeq(req.getAppId() + ":" + Constants.SeqConstants.Friendship);
        ImFriendShipEntity update = new ImFriendShipEntity();
        update.setFriendSequence(seq);
        update.setBlack(FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode());
        int update1 = imFriendShipMapper.update(update, query);

        if (update1 == 1) {
            writeUserSeq.writeUserSeq(req.getAppId(),req.getFromId(),Constants.SeqConstants.Friendship,seq);
            DeleteBlackPack deleteFriendPack = new DeleteBlackPack();
            deleteFriendPack.setFromId(req.getFromId());
            deleteFriendPack.setSequence(seq);
            deleteFriendPack.setToId(req.getToId());
            messageProducer.sendToUser(req.getFromId(), req.getClientType(), req.getImei(), FriendshipEventCommand.FRIEND_BLACK_DELETE,
                    deleteFriendPack, req.getAppId());

            //之后回调
            if (appConfig.isAddFriendShipBlackAfterCallback()) {
                AddFriendBlackAfterCallbackDto callbackDto = new AddFriendBlackAfterCallbackDto();
                callbackDto.setFromId(req.getFromId());
                callbackDto.setToId(req.getToId());
                callbackService.callback(req.getAppId(),
                        Constants.CallbackCommand.DeleteBlack, JSONObject
                                .toJSONString(callbackDto));
            }
        }
        return ResponseVO.successResponse();
    }

    //校验黑名单
    @Override
    public ResponseVO checkBlack(CheckFriendShipReq req) {
        //toId改成一个map key是toid的值 value是0
        Map<String, Integer> result
                = req.getToIds().stream()
                .collect(Collectors.toMap(Function.identity(), s -> 0));

        List<CheckFriendShipResp> resp = new ArrayList<>();
        //判断校验方式 单向 双向
        if (req.getCheckType() == CheckFriendShipTypeEnum.SINGLE.getType()) {
            resp = imFriendShipMapper.checkFriendShipBlack(req);
        } else {
            resp = imFriendShipMapper.checkFriendShipBlackBoth(req);
        }
        //数据库返回的数据resp转成map，，key是toId，value是status
        Map<String, Integer> collect = resp.stream()
                .collect(Collectors.toMap(CheckFriendShipResp::getToId
                        , CheckFriendShipResp::getStatus));

        for (String toId : result.keySet()) {
            if (!collect.containsKey(toId)) {//结果集中判断传入的toId是否都存在，不存在就新建一个对象
                CheckFriendShipResp checkFriendShipResp = new CheckFriendShipResp();
                checkFriendShipResp.setFromId(req.getFromId());
                checkFriendShipResp.setToId(toId);
                checkFriendShipResp.setStatus(result.get(toId));//状态默认为0
                resp.add(checkFriendShipResp);
            }
        }
        return ResponseVO.successResponse(resp);
    }

    //实现增量拉取的流程
    @Override
    public ResponseVO syncFriendshipList(SyncReq req) {

        if (req.getMaxLimit() > 100) {
            req.setMaxLimit(100);
        }
        SyncResp<ImFriendShipEntity> resp = new SyncResp<>();
        //seq > req.getseq limit maxLimit
        QueryWrapper<ImFriendShipEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("from_id",req.getOperater());
        queryWrapper.gt("friend_sequence",req.getLastSequence());//大于
        queryWrapper.eq("app_id",req.getAppId());
        queryWrapper.last(" limit " + req.getMaxLimit());
        queryWrapper.orderByAsc("friend_sequence");
        List<ImFriendShipEntity> list = imFriendShipMapper.selectList(queryWrapper);

        if(!CollectionUtils.isEmpty(list)){
            ImFriendShipEntity maxSeqEntity = list.get(list.size() - 1);//获取seq最大的元素
            resp.setDataList(list);
            //设置最大seq
            Long friendShipMaxSeq = imFriendShipMapper.getFriendShipMaxSeq(req.getAppId(), req.getOperater());
            resp.setMaxSequence(friendShipMaxSeq);
            //设置是否拉取完毕              查询到的seq >= 数据库中已写入存在的最大seq 就拉取完毕！
            resp.setCompleted(maxSeqEntity.getFriendSequence() >= friendShipMaxSeq);
            return ResponseVO.successResponse(resp);
        }
        resp.setCompleted(true);
        return ResponseVO.successResponse(resp);
    }

    @Override
    public List<String> getAllFriendId(String userId, Integer appId) {
        return imFriendShipMapper.getAllFriendId(userId, appId);
    }
}
