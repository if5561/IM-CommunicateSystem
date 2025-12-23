package com.lld.im.common.route.algorithm.consistenthash;

import com.lld.im.common.enums.UserErrorCode;
import com.lld.im.common.exception.ApplicationException;

import java.util.SortedMap;
import java.util.TreeMap;

//子类 拓展哈希算法
public class TreeMapConsistentHash extends AbstractConsistentHash {

    private TreeMap<Long,String> treeMap = new TreeMap<>();

    private static final int NODE_SIZE=2;//每个真实节点对应 2 个虚拟节点

    @Override
    protected void add(long key, String value) {
        for (int i = 0; i < NODE_SIZE; i++) {
            treeMap.put(super.hash("node"+key+i),value);
            //加点料，， 计算虚拟节点哈希值并且将虚拟节点映射到真实节点
        }
        treeMap.put(key,value); //添加真实节点：key 是真实节点地址的哈希值，value 是真实节点地址
    }

    @Override
    protected String getFirstNodeValue(String value) {
        //value就是userId 拿出userId的哈希值 去treemap中间找离他最近的结点
        Long hash = super.hash(value);
        SortedMap<Long, String> last = treeMap.tailMap(hash);
        if(!last.isEmpty()){
            return last.get(last.firstKey());// 取第一个节点（顺时针最近
        }
        if(treeMap.size()==0){
            throw new ApplicationException(UserErrorCode.SERVER_NOT_AVAILABLE);
        }
        return treeMap.firstEntry().getValue();// 若用户哈希值在哈希环末尾，取第一个节点（闭环特性）
    }

    @Override
    protected void processBefore() {
        treeMap.clear();//结点是动态的 要清空map
    }
}
