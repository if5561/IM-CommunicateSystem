package com.lld.im.service.friendship.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lld.im.service.friendship.dao.ImFriendShipEntity;
import com.lld.im.service.friendship.model.req.CheckFriendShipReq;

import com.lld.im.service.friendship.model.resp.CheckFriendShipResp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ImFriendShipMapper extends BaseMapper<ImFriendShipEntity> {
        //单向校验逻辑                                       //status=1就返回1 其他返回0

    /**
     * select from_id as fromId, to_id as toId, if(status = 1,1,0) as status
     * from im_friendship
     * where from_id = #{fromId}
     * and to_id in (...)  -- 你传入的 toIds 列表
     * and app_id = #{appId}
     * @param req
     * @return
     */
    @Select("<script>"+
            "select from_id as fromId , to_id as toId ,if(status = 1,1,0) as status from im_friendship where from_id = #{fromId} and to_id in " +
            "<foreach collection='toIds' index='index' item='id' separator=',' close = ')' open='(' > " +
            "#{id}" +
            "</foreach>" +
            " and app_id = #{appId} " +
            "</script>")
    List<CheckFriendShipResp> checkFriendShip(CheckFriendShipReq req);


    @Select("<script>" +
            " select a.fromId,a.toId , ( \n" +
            " case \n" +
            " when a.status = 1 and b.status = 1 then 1 \n" +
            " when a.status = 1 and b.status != 1 then 2 \n" +
            " when a.status != 1 and b.status = 1 then 3 \n" +
            " when a.status != 1 and b.status != 1 then 4 \n" +
            " end \n" +
            " ) \n " +
            " as status from "+
            " (select from_id AS fromId , to_id AS toId , if(status = 1,1,0) as status from im_friendship where app_id = #{appId} and from_id = #{fromId} AND to_id in " +
            "<foreach collection='toIds' index='index' item='id' separator=',' close=')' open='('>" +
            " #{id} " +
            "</foreach>" +
            " ) as a INNER join" +
            " (select from_id AS fromId, to_id AS toId , if(status = 1,1,0) as status from im_friendship where app_id = #{appId} and to_id = #{fromId} AND from_id in " +
            "<foreach collection='toIds' index='index' item='id' separator=',' close=')' open='('>" +
            " #{id} " +
            "</foreach>" +
            " ) as b " +
            " on a.fromId = b.toId AND b.fromId = a.toId "+
            "</script>"
        )
    List<CheckFriendShipResp> checkFriendShipBoth(CheckFriendShipReq req);

    @Select("<script>" +
                   " select from_id AS fromId, to_id AS toId , if(black = 1,1,0) as status from im_friendship where app_id = #{appId} and from_id = #{fromId}  and  to_id in " +
                   "<foreach collection='toIds' index='index' item='id' separator=',' close=')' open='('>" +
                   " #{id} " +
                   "</foreach>" +
                   "</script>"
    )
    List<CheckFriendShipResp> checkFriendShipBlack(CheckFriendShipReq req);

    @Select("<script>" +
            " select a.fromId,a.toId , ( \n" +
            " case \n" +
            " when a.black = 1 and b.black = 1 then 1 \n" +
            " when a.black = 1 and b.black != 1 then 2 \n" +
            " when a.black != 1 and b.black = 1 then 3 \n" +
            " when a.black != 1 and b.black != 1 then 4 \n" +
            " end \n" +
            " ) \n " +
            " as status from "+
            " (select from_id AS fromId , to_id AS toId , if(black = 1,1,0) as black from im_friendship where app_id = #{appId} and from_id = #{fromId} AND to_id in " +
            "<foreach collection='toIds' index='index' item='id' separator=',' close=')' open='('>" +
            " #{id} " +
            "</foreach>" +
            " ) as a INNER join" +
            " (select from_id AS fromId, to_id AS toId , if(black = 1,1,0) as black from im_friendship where app_id = #{appId} and to_id = #{fromId} AND from_id in " +
            "<foreach collection='toIds' index='index' item='id' separator=',' close=')' open='('>" +
            " #{id} " +
            "</foreach>" +
            " ) as b " +
            " on a.fromId = b.toId AND b.fromId = a.toId "+
            "</script>"
    )
    List<CheckFriendShipResp> checkFriendShipBlackBoth(CheckFriendShipReq toId);


    @Select(" select max(friend_sequence) from im_friendship where app_id = #{appId} AND from_id = #{userId} ")
    Long getFriendShipMaxSeq(Integer appId,String userId);

    @Select(
            " select to_id from im_friendship where from_id = #{userId} AND app_id = #{appId} and status = 1 and black = 1 "
    )
    List<String> getAllFriendId(String userId,Integer appId);
}
