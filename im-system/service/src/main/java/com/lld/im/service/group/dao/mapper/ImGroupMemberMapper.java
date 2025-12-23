package com.lld.im.service.group.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lld.im.common.ResponseVO;
import com.lld.im.service.group.dao.ImGroupMemberEntity;
import com.lld.im.service.group.model.req.GetJoinedGroupReq;
import com.lld.im.service.group.model.req.GroupMemberDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Mapper
@Repository //如果你的项目中存在 手动编写的 DAO 实现类
// （而非 MyBatis-Plus 的 Mapper 接口），则需要添加 @Repository 注解
public interface ImGroupMemberMapper extends BaseMapper<ImGroupMemberEntity> {

    @Select("select group_id from im_group_member where app_id = #{appId} and member_id = #{memberId}")
    ResponseVO<Collection<String>> getJoinedGroupId(GetJoinedGroupReq req);

    //role != LEAVE
    @Select("select group_id from im_group_member where app_id = #{appId} AND member_id = #{memberId} and role != #{role}" )
    public List<String> syncJoinedGroupId(Integer appId, String memberId, int role);


    //定义一个 SQL 查询，并指定如何将数据库查询出的列名映射到 Java 对象（GroupMemberDto）的属性名上。
    @Results({
            @Result(column = "member_id", property = "memberId"),
//            @Result(column = "speak_flag", property = "speakFlag"),
            @Result(column = "speak_date", property = "speakDate"),
            @Result(column = "role", property = "role"),
            @Result(column = "alias", property = "alias"),
            @Result(column = "join_time", property = "joinTime"),
            @Result(column = "join_type", property = "joinType")
    })
    @Select("select " +
            " member_id, " +
//            " speak_flag,  " +
            " speak_date,  " +
            " role, " +
            " alias, " +
            " join_time ," +
            " join_type " +
            " from im_group_member where app_id = #{appId} AND group_id = #{groupId} ")
    public List<GroupMemberDto> getGroupMember(Integer appId, String groupId);

    @Select("select " +
            " member_id " +
            " from im_group_member where app_id = #{appId} AND group_id = #{groupId} and role != 3")
    public List<String> getGroupMemberId(Integer appId, String groupId);



    @Select("select " +
            " member_id, " +
//            " speak_flag,  " +
            " role " +
//            " alias, " +
//            " join_time ," +
//            " join_type " +
            " from im_group_member where app_id = #{appId} AND group_id = #{groupId} and role in (1,2) ")
    public List<GroupMemberDto> getGroupManager(String groupId, Integer appId);
}
