package com.lld.im.service.user.models.resp;


import com.lld.im.service.user.dao.ImUserDataEntity;
import lombok.Data;

import java.util.List;

@Data
public class GetUserInfoResp {
    private List<ImUserDataEntity> userDataItem;
    private List<String> failUser;
}

