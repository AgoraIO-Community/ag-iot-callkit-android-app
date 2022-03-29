package com.agora.agoracallkit.beans;

public class RtmServerInfoBean {
    private String mBaseName = "";
    private int mNodeCount = 0;

    public void setBaseName(String baseName) {
        mBaseName = baseName;
    }

    public void setNodeCount(int nodeCount) {
        mNodeCount = nodeCount;
    }

    public void updateInfo(RtmServerInfoBean info) {
        mBaseName = info.mBaseName;
        mNodeCount = info.mNodeCount;
    }

    public String getBaseName() {
        return mBaseName;
    }

    public int getNodeCount() {
        return mNodeCount;
    }
}
