package com.agora.agoracallkit.beans;

public class ListenerInfoBean {
    private String account  = "";
    private int type = -1;

    public String getAccount() {
        return account;
    }

    public int getType() {
        return type;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public void setType(int type) {
        this.type = type;
    }

    public void update(ListenerInfoBean info) {
        this.account = info.account;
        this.type = info.type;
    }

    public void clear() {
        this.account = "";
        this.type = -1;
    }
}
