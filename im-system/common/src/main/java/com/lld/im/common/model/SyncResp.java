package com.lld.im.common.model;

import lombok.Data;

import java.util.List;

/**
 * @author: Chackylee
 * @description:
 **/
@Data
public class SyncResp<T> {

    private Long maxSequence;//本次拉取最大的seq

    private boolean isCompleted;//是否完成了全部seq的拉取

    private List<T> dataList;

}
