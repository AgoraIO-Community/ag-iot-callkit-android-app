package com.agora.agoracallkit.beans;

import java.util.HashMap;
import java.util.Map;

//用于保持运行过程中查询、注册获取到的UID和account间的映射，减少业务服务查询次数
public class UidInfoBeansMap {
    private static final String TAG = UidInfoBeansMap.class.getSimpleName();
    private static UidInfoBeansMap instance;

    private Map<Long, UidInfoBean> mUidInfoMap = new HashMap<Long, UidInfoBean>();
    private Map<String, UidInfoBean> mAccountInfoMap = new HashMap<String, UidInfoBean>();

    public static UidInfoBeansMap getInstance() {
        if(instance == null) {
            synchronized (UidInfoBeansMap.class) {
                if(instance == null) {
                    instance = new UidInfoBeansMap();
                }
            }
        }
        return instance;
    }

    public UidInfoBean searchUidInfo(long uid) {
        return mUidInfoMap.get(uid);
    }

    public UidInfoBean searchUidInfo(String account) {
        return mAccountInfoMap.get(account);
    }

    public void saveUidInfo(UidInfoBean info) {
//        if (mUidInfoMap.containsKey(info.getUid())) {
//            mUidInfoMap.replace(info.getUid(), info);
//        } else {
//            mUidInfoMap.put(info.getUid(), info);
//        }
//        if (mAccountInfoMap.containsKey(info.getAccount())) {
//            mAccountInfoMap.replace(info.getAccount(), info);
//        } else {
//            mAccountInfoMap.put(info.getAccount(), info);
//        }
        mUidInfoMap.put(info.getUid(), info);
        mAccountInfoMap.put(info.getAccount(), info);
    }

    public boolean removeUidInfo(long uid) {
        UidInfoBean chaekInfo = mUidInfoMap.get(uid);
        if (chaekInfo == null) {
            return false;
        }
        if (mAccountInfoMap.containsKey(chaekInfo.getAccount())) {
            mAccountInfoMap.remove(chaekInfo.getAccount());
        }
        mUidInfoMap.remove(uid);
        return true;
    }

    public boolean removeUidInfo(String account) {
        UidInfoBean chaekInfo = mAccountInfoMap.get(account);
        if (chaekInfo == null) {
            return false;
        }
        if (mUidInfoMap.containsKey(chaekInfo.getUid())) {
            mUidInfoMap.remove(chaekInfo.getUid());
        }
        mAccountInfoMap.remove(account);
        return true;
    }

    public void clearUidInfoMap() {
        mUidInfoMap.clear();
        mAccountInfoMap.clear();
    }
}
