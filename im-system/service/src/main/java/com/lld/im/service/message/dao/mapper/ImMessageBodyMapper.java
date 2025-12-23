package com.lld.im.service.message.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lld.im.service.message.dao.ImMessageBodyEntity;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;
@Mapper
@Repository
public interface ImMessageBodyMapper extends BaseMapper<ImMessageBodyEntity> {
}
