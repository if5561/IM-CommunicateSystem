package com.lld.im.common.enums.command;

public enum UserEventCommand implements Command {

    //用户修改command 4000
    USER_MODIFY(4000),

    //4001
    USER_ONLINE_STATUS_CHANGE(4001),


    //4004 用户在线状态通知报文
    USER_ONLINE_STATUS_CHANGE_NOTIFY(4004),

    //4005 用户在线状态通知同步报文
    USER_ONLINE_STATUS_CHANGE_NOTIFY_SYNC(4005),


    ;

    private int command;

    UserEventCommand(int command){
        this.command=command;
    }


    @Override
    public int getCommand() {
        return command;
    }
}
