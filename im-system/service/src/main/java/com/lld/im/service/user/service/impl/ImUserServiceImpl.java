package com.lld.im.service.user.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.api.R;
import com.lld.im.codec.pack.user.UserModifyPack;
import com.lld.im.common.Constants.Constants;
import com.lld.im.common.ResponseVO;
import com.lld.im.common.config.AppConfig;
import com.lld.im.common.enums.DelFlagEnum;
import com.lld.im.common.enums.command.UserEventCommand;
import com.lld.im.common.exception.ApplicationException;
import com.lld.im.service.group.service.ImGroupService;
import com.lld.im.service.user.dao.ImUserDataEntity;
import com.lld.im.service.user.dao.mapper.ImUserDataMapper;
import com.lld.im.service.user.models.req.*;
import com.lld.im.service.user.models.resp.GetUserInfoResp;
import com.lld.im.service.user.models.resp.ImportUserResp;
import com.lld.im.service.user.service.ImUserService;
import com.lld.im.service.utils.CallbackService;
import com.lld.im.service.utils.MessageProducer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.lld.im.common.enums.UserErrorCode;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImUserServiceImpl implements ImUserService {

    @Autowired
    ImUserDataMapper imUserDataMapper;

    @Autowired
    AppConfig appConfig;

    @Autowired
    CallbackService callbackService;

    @Autowired
    MessageProducer messageProducer;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    ImGroupService imGroupService;

    //添加用户
    @Override
    public ResponseVO importUser(ImportUserReq req) {


        if (req.getUserData().size() > 100) {
            return ResponseVO.errorResponse(UserErrorCode.IMPORT_SIZE_BEYOND);
        }
        List<String> successId = new ArrayList<>();
        List<String> errorId = new ArrayList<>();
        req.getUserData().forEach(e->{

            try {
                e.setAppId(req.getAppId());
                int insert = imUserDataMapper.insert(e);
                //返回值表示成功插入的记录条数
                if(insert==1){
                    successId.add(e.getUserId());//插入成功
                }
            }catch (Exception ex){
                ex.printStackTrace();
                errorId.add(e.getUserId());
            }
        });
        ImportUserResp resp = new ImportUserResp();
        resp.setSuccessId(successId);
        resp.setErrorId(errorId);
        return ResponseVO.successResponse(resp);
    }

    //查询用户 根据appId和userId
    @Override
    public ResponseVO<GetUserInfoResp> getUserInfo(GetUserInfoReq req) {
        QueryWrapper<ImUserDataEntity> queryWrapper =new QueryWrapper<>();
        queryWrapper.eq("app_id",req.getAppId());
        queryWrapper.in("user_id",req.getUserIds());
        queryWrapper.eq("del_flag", DelFlagEnum.NORMAL.getCode());

        List<ImUserDataEntity> userDataEntities= imUserDataMapper.selectList(queryWrapper);
        HashMap<String, ImUserDataEntity> map = new HashMap<>();

        for(ImUserDataEntity data:
                userDataEntities){
            map.put(data.getUserId(),data);
        }

        List<String> failUser = new ArrayList<>();
        for(String uid:
              req.getUserIds()){
            if(!map.containsKey(uid)){
                failUser.add(uid);
            }
        }//map中不存在 未查询到数据 id放入failuser中

        GetUserInfoResp resp = new GetUserInfoResp();
        resp.setUserDataItem(userDataEntities);
        resp.setFailUser(failUser);
        return ResponseVO.successResponse(resp);
    }

    //查询单个用户
    @Override
    public ResponseVO<ImUserDataEntity> getSingleUserInfo(String useId, Integer appId) {
        QueryWrapper objectQueryWrapper = new QueryWrapper<>();
        objectQueryWrapper.eq("user_id",useId);
        objectQueryWrapper.eq("app_id",appId);
        objectQueryWrapper.eq("del_flag", DelFlagEnum.NORMAL.getCode());

        ImUserDataEntity imUserDataEntity = imUserDataMapper.selectOne(objectQueryWrapper);
        if(imUserDataEntity==null){
            return ResponseVO.errorResponse(UserErrorCode.USER_IS_NOT_EXIST);
        }
        return ResponseVO.successResponse(imUserDataEntity);
    }

    //删除用户 逻辑删除 并非物理删除 只是将 实体类的delFlag置为1
    @Override
    public ResponseVO deleteUser(DeleteUserReq req) {
        ImUserDataEntity entity =new ImUserDataEntity();
        entity.setDelFlag(DelFlagEnum.DELETE.getCode());//设置delflag为1（删除）

        List<String> errorId =new ArrayList<>();
        List<String> successId = new ArrayList<>();

        for(String userId:req.getUserId()){
            QueryWrapper wrapper = new QueryWrapper<>();
            wrapper.eq("app_id",req.getAppId());
            wrapper.eq("user_id",userId);
            wrapper.eq("del_flag",DelFlagEnum.NORMAL.getCode());//用户当前状态不是删除状态的
                                                            //防止重复删除已经被标记为删除的用户
            int update=0;//影响的结果行数
            try {
                update = imUserDataMapper.update(entity,wrapper);
                if(update>0){
                    successId.add(userId);//影响条数>0,删除成功
                }else{
                    errorId.add(userId);//删除失败
                }
            }catch (Exception e){
                errorId.add(userId);
            }
        }
        ImportUserResp resp=new ImportUserResp();
        resp.setSuccessId(successId);
        resp.setErrorId(errorId);
        return ResponseVO.successResponse(resp);
    }

    @Override
    public ResponseVO login(LoginReq req) {
        return ResponseVO.successResponse();
    }

    //修改用户
    @Override
    @Transactional //开启事务 保存修改的一致性
    public ResponseVO modifyUserInfo(ModifyUserInfoReq req) {
        QueryWrapper query = new QueryWrapper<>();
        query.eq("app_id",req.getAppId());
        query.eq("user_id",req.getUserId());
        query.eq("del_flag",DelFlagEnum.NORMAL.getCode());
        ImUserDataEntity user =imUserDataMapper.selectOne(query);
        if (user == null) {
            throw new ApplicationException(UserErrorCode.USER_IS_NOT_EXIST);
        }

        ImUserDataEntity update =new ImUserDataEntity();
        BeanUtils.copyProperties(req,update);

        update.setAppId(null);
        update.setUserId(null);
        int update1=imUserDataMapper.update(update,query);
        if(update1==1){
            //todo 回调
            //发送tcp通知 给其他端
            UserModifyPack pack = new UserModifyPack();
            BeanUtils.copyProperties(req,pack);
            messageProducer.sendToUser(req.getUserId(),req.getClientType(),req.getImei(),
                    UserEventCommand.USER_MODIFY,pack,req.getAppId());
            
            if(appConfig.isModifyUserAfterCallback()){
                callbackService.callback(req.getAppId(),
                        Constants.CallbackCommand.ModifyUserAfter,
                        JSONObject.toJSONString(req));
            }
            return ResponseVO.successResponse();
        }
        throw new ApplicationException(UserErrorCode.MODIFY_USER_ERROR);
    }

    @Override
    public ResponseVO getUserSequence(GetUserSequenceReq req) {
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(req.getAppId() + ":" + Constants.RedisConstants.SeqPrefix + ":" + req.getUserId());
        //获取该用户加入群组的最大seq
        Long groupSeq = imGroupService.getUserGroupMaxSeq(req.getUserId(),req.getAppId());
        map.put(Constants.SeqConstants.Group,groupSeq);
        return ResponseVO.successResponse(map);
    }


}
