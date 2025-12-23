package com.lld.im.common.route.algorithm.consistenthash;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

//用来标识哈希算法
//一致性hash抽象类
public abstract class AbstractConsistentHash {
/*因为需要拓展多种方式实现一致性哈希 所以定义为抽象类 供子类拓展*/

    //add
    protected abstract void add(long key,String value);

    //sort
    protected void sort(){}

    //获取节点 get
    protected abstract String getFirstNodeValue(String value);

    /**
     * 处理之前事件
     */
    protected abstract void processBefore();

    /**
     * 传入节点列表以及客户端信息获取一个服务节点
     * @param values
     * @param key
     * @return
     */
    public synchronized String process(List<String> values, String key){
        processBefore();
        for (String value : values) {
            add(hash(value), value);// 子类实现：添加节点到哈希环
        }
        sort();
        return getFirstNodeValue(key) ;//获取服务节点  子类实现：路由用户到节点
    }

    //hash
    /**
     * hash 运算
     * @param value
     * @return
     */
    public Long hash(String value){
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not supported", e);
        }
        md5.reset();
        byte[] keyBytes = null;
        try {
            keyBytes = value.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Unknown string :" + value, e);
        }

        md5.update(keyBytes);
        byte[] digest = md5.digest();

        // hash code, Truncate to 32-bits
        long hashCode = ((long) (digest[3] & 0xFF) << 24)
                | ((long) (digest[2] & 0xFF) << 16)
                | ((long) (digest[1] & 0xFF) << 8)
                | (digest[0] & 0xFF);

        long truncateHashCode = hashCode & 0xffffffffL;
        return truncateHashCode;
    }

}
