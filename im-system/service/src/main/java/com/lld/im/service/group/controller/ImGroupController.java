package com.lld.im.service.group.controller;

import com.lld.im.common.ResponseVO;
import com.lld.im.common.model.SyncReq;
import com.lld.im.service.group.model.req.*;
import com.lld.im.service.group.service.GroupMessageService;
import com.lld.im.service.group.service.ImGroupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@Slf4j
@RestController
@RequestMapping("v1/group")
public class ImGroupController {

    @Autowired
    ImGroupService groupService;

    @Autowired
    GroupMessageService groupMessageService;

    @RequestMapping("/importGroup")
    public ResponseVO importGroup(@RequestBody @Validated ImportGroupReq req, Integer appId, String identifier){
        req.setAppId(appId);
        req.setOperater(identifier);
        //log.info("appId: {}, identifier: {}", appId, identifier);
        return groupService.importGroup(req);
    }
    @RequestMapping("/createGroup")
    public ResponseVO createGroup(@RequestBody @Validated CreateGroupReq req, Integer appId, String identifier)  {
        req.setAppId(appId);
        req.setOperater(identifier);
        return groupService.createGroup(req);
    }

    @RequestMapping("/update")
    public ResponseVO update(@RequestBody @Validated UpdateGroupReq req, Integer appId, String identifier) {
        req.setAppId(appId);
        req.setOperater(identifier);
        return groupService.updateBaseGroupInfo(req);
    }

    @RequestMapping("/getGroupInfo")
    public ResponseVO getGroupInfo(@RequestBody @Validated GetGroupInfoReq req, Integer appId)  {
        req.setAppId(appId);
        return groupService.getGroupInfo(req);
    }

    @RequestMapping("/getJoinedGroup")
    public ResponseVO getJoinedGroup(@RequestBody @Validated GetJoinedGroupReq req, Integer appId, String identifier)  {
        req.setAppId(appId);
        req.setOperater(identifier);
        return groupService.getJoinedGroup(req);
    }

    @RequestMapping("/destroyGroup")
    public ResponseVO destroyGroup(@RequestBody @Validated DestroyGroupReq req, Integer appId, String identifier)  {
        req.setAppId(appId);
        req.setOperater(identifier);
        return groupService.destroyGroup(req);
    }

    @RequestMapping("/transferGroup")
    public ResponseVO transferGroup(@RequestBody @Validated TransferGroupReq req, Integer appId, String identifier)  {
        req.setAppId(appId);
        req.setOperater(identifier);
        return groupService.transferGroup(req);
    }

    @RequestMapping("/forbidSendMessage")
    public ResponseVO forbidSendMessage(@RequestBody @Validated MuteGroupReq req, Integer appId, String identifier)  {
        req.setAppId(appId);
        req.setOperater(identifier);
        return groupService.muteGroup(req);
    }

    @RequestMapping("/sendMessage")
    public ResponseVO sendMessage(@RequestBody @Validated SendGroupMessageReq
                                          req, Integer appId,
                                  String identifier)  {
        req.setAppId(appId);
        req.setOperater(identifier);
        return ResponseVO.successResponse(groupMessageService.send(req));
    }

    @RequestMapping("/syncJoinedGroup")
    public ResponseVO syncJoinedGroup(@RequestBody @Validated SyncReq req, Integer appId, String identifier)  {
        req.setAppId(appId);
        return groupService.syncJoinedGroupList(req);
    }
}
