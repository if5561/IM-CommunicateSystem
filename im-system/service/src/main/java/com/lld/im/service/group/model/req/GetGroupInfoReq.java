package com.lld.im.service.group.model.req;

import com.lld.im.common.model.RequestBase;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * @author: Chackylee
 * @description:
 **/
@Data
public class GetGroupInfoReq extends RequestBase {
    @NotBlank(message = "群id不能为空")
    private String groupId;

}
