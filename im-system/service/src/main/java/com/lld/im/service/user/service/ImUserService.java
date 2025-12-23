package com.lld.im.service.user.service;

import com.baomidou.mybatisplus.extension.api.R;
import com.lld.im.common.ResponseVO;
import com.lld.im.service.user.dao.ImUserDataEntity;
import com.lld.im.service.user.models.req.*;
import com.lld.im.service.user.models.resp.GetUserInfoResp;
import lombok.Data;


public interface ImUserService {
    //添加用户
    public ResponseVO importUser(ImportUserReq req);

    //查询多个用户
    public ResponseVO<GetUserInfoResp> getUserInfo(GetUserInfoReq req);

    //查询单个用户 根据userId和appId
    public ResponseVO<ImUserDataEntity> getSingleUserInfo(String useId,Integer appId);

    //删除用户
    public ResponseVO deleteUser(DeleteUserReq req);

    ResponseVO login(LoginReq req);

    //修改用户
    public ResponseVO modifyUserInfo(ModifyUserInfoReq req);

    //查询用户的seq 方便和服务端的seq作比较 看要不要更新
    ResponseVO getUserSequence(GetUserSequenceReq req);
}
