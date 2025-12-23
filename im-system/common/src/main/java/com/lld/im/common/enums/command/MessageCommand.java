package com.lld.im.common.enums.command;

public enum MessageCommand implements Command {

    //单聊消息 1103
    MSG_P2P(0x44F),

    //单聊消息ACK 1046
    MSG_ACK(0x416),

    //消息收到ack 1107
    MSG_RECIVE_ACK(1107),

    //发送消息已读   1106
    MSG_READED(0x452),

    //消息已读通知给同步端 1053
    MSG_READED_NOTIFY(0x41D),

    //消息已读回执，给原消息发送方 1054
    MSG_READED_RECEIPT(0x41E),

    //消息撤回 1050
    MSG_RECALL(0x41A),

    //消息撤回通知 1052
    MSG_RECALL_NOTIFY(0x41C),

    //消息撤回回报 1051
    MSG_RECALL_ACK(0x41B),

    ;

    private int command;

    MessageCommand(int command){
        this.command=command;
    }


    @Override
    public int getCommand() {
        return command;
    }
}
