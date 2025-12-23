package com.lld.im.tcp.reciver.process;



public class  ProcessFactory {

    private static BaseProcess defaultProcess;

    static {
        defaultProcess = new BaseProcess() {
            @Override
            public void processBefore() {

            }

            @Override
            public void processAfter() {

            }
        };
    }

    public static BaseProcess getMessageProcess(Integer command){
        //根据不同的command返回不同BaseProcess
        return defaultProcess;
    }

}
