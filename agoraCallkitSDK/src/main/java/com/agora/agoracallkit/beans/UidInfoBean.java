package com.agora.agoracallkit.beans;

public class UidInfoBean {
    public static final int TYPE_MAP_DEVICE = 1;
    public static final int TYPE_MAP_USER = 2;

    private long mUid = -1;
    private int mType = -1;
    private String mAccount = "";

    public long getUid() {
        return mUid;
    }

    public void setUid(long uid) {
        mUid = uid;
    }

    public int getType() {
        return mType;
    }

    public void setType(int type) {
        mType = type;
    }

    public String getAccount() {
        return mAccount;
    }

    public void setAccount(String account) {
        mAccount = account;
    }

    public void updateInfo(UidInfoBean info) {
        mUid = info.mUid;
        mType = info.mType;
        mAccount = info.mAccount;
    }

    public void clearInfo() {
        mUid = -1;
        mType = -1;
        mAccount = "";
    }

    @Override
    public String toString() {
        String temp = "account=" + mAccount + ", type=" + mType + ", uid=" + mUid;
        return temp;
    }
}
