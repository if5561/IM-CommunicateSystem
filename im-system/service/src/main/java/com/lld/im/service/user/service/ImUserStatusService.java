package com.lld.im.service.user.service;

import com.lld.im.common.ResponseVO;
import com.lld.im.service.user.models.UserStatusChangeNotifyContent;
import com.lld.im.service.user.models.req.PullFriendOnlineStatusReq;
import com.lld.im.service.user.models.req.PullUserOnlineStatusReq;
import com.lld.im.service.user.models.req.SetUserCustomerStatusReq;
import com.lld.im.service.user.models.req.SubscribeUserOnlineStatusReq;
import com.lld.im.service.user.models.resp.UserOnlineStatusResp;
//import com.lld.im.service.user.model.UserStatusChangeNotifyContent;
//import com.lld.im.service.user.model.req.PullFriendOnlineStatusReq;
//import com.lld.im.service.user.model.req.PullUserOnlineStatusReq;
//import com.lld.im.service.user.model.req.SetUserCustomerStatusReq;
//import com.lld.im.service.user.model.req.SubscribeUserOnlineStatusReq;
//import com.lld.im.service.user.model.resp.UserOnlineStatusResp;

import java.util.Map;

/**
 * @description:
 * @author: lld
 * @version: 1.0
 */
public interface ImUserStatusService {

    public void processUserOnlineStatusNotify(UserStatusChangeNotifyContent content);

    void subscribeUserOnlineStatus(SubscribeUserOnlineStatusReq req);

    void setUserCustomerStatus(SetUserCustomerStatusReq req);

    Map<String, UserOnlineStatusResp> queryFriendOnlineStatus(PullFriendOnlineStatusReq req);

    Map<String, UserOnlineStatusResp> queryUserOnlineStatus(PullUserOnlineStatusReq req);
}
