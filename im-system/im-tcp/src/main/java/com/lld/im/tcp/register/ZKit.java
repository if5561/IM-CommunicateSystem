package com.lld.im.tcp.register;

import com.lld.im.common.Constants.Constants;
import org.I0Itec.zkclient.ZkClient;

public class ZKit {

    private ZkClient zkClient;

    public ZKit(ZkClient zkClient) {
        this.zkClient = zkClient;
    }

    //im-coreRoot/tcp/ip:port
    public void createRootNode(){
        //创建父节点
        boolean exists = zkClient.exists(Constants.ImCoreZkRoot);
        if(!exists){
            zkClient.createPersistent(Constants.ImCoreZkRoot);
        }
        boolean tcpExists = zkClient.exists(Constants.ImCoreZkRoot +
                Constants.ImCoreZkRootTcp);
        if(!tcpExists){
            zkClient.createPersistent(Constants.ImCoreZkRoot +
                    Constants.ImCoreZkRootTcp);
        }

        boolean webExists = zkClient.exists(Constants.ImCoreZkRoot +
                Constants.ImCoreZkRootWeb);
        if(!webExists){
            zkClient.createPersistent(Constants.ImCoreZkRoot +
                    Constants.ImCoreZkRootWeb);
        }
    }

    //ip+port
    public void createNode(String path){
        if(!zkClient.exists(path)){
            zkClient.createPersistent(path);
        }
    }
}
