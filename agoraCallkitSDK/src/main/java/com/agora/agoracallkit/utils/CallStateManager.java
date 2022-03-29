package com.agora.agoracallkit.utils;

import android.util.Log;

import com.agora.agoracallkit.beans.UidInfoBean;
import com.agora.agoracallkit.logger.ALog;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CallStateManager {
    public static final int CALL_STATE_IDLE = 0;       //空闲状态
    public static final int CALL_STATE_CALLING = 1;    //正在呼叫
    public static final int CALL_STATE_TALKING = 2;    //正在通话

    private static final String TAG = "CALLKIT/CallStateMgr";
    private static CallStateManager instance;

    private long mCurCallSessionId = -1;
    private String mCurCallChannel = "";
    private String mCurCallToken = "";
    private int mCurCallState = CALL_STATE_IDLE;
    private UidInfoBean mLocalUser = new UidInfoBean();     //当前本地登录用户信息
    private UidInfoBean mRemoteUser = new UidInfoBean();    //当前呼叫会话对端用户信息
    private List<UidInfoBean> mPerRemoteUsers = new ArrayList<UidInfoBean>();   //一次呼叫多个对端暂存

    public static CallStateManager getInstance() {
        if(instance == null) {
            synchronized (CallStateManager.class) {
                if(instance == null) {
                    instance = new CallStateManager();
                }
            }
        }
        return instance;
    }

    //获取当前本地登录用户信息
    public UidInfoBean getLocalUser() {
        return mLocalUser;
    }

    //获取当前呼叫会话对端用户信息
    public UidInfoBean getRemoteUser() {
        return mRemoteUser;
    }

    //获取暂未建立连接的呼叫用户列表
    public List<UidInfoBean> getNoResponseRemoteUsers() {
        List<UidInfoBean> res = new ArrayList<UidInfoBean>();
        for (int i = 0; i < mPerRemoteUsers.size(); i++) {
            res.add(mPerRemoteUsers.get(i));
        }
        return res;
    }

    //清空暂未建立连接的呼叫用户列表
    public void clearNoResponseRemoteUsers() {
        mPerRemoteUsers.clear();
    }

    //获取当前呼叫状态，空闲/呼叫中/通话中
    public int getCurCallState() {
        return mCurCallState;
    }

    //更新当前登录的用户信息
    public synchronized void updateLocalUserInfo(long uid, String account, int type) {
        mLocalUser.setType(type);
        mLocalUser.setUid(uid);
        mLocalUser.setAccount(account);
    }

    //清除当前登录的用户信息
    public synchronized void clearLocalUserInfo() {
        mLocalUser.clearInfo();
    }

    //获取当前通话分配的channel参数
    public String getCurCallChannel() {
        return mCurCallChannel;
    }

    //获取当前通话分配的token参数
    public String getCurCallToken() {
        return mCurCallToken;
    }

    //获取当前通话中的session ID
    public long getCurSessionId() {
        return mCurCallSessionId;
    }

    //创建一个新的呼叫会话，返回session ID，-1说明有正在进行会话，不允许创建新会话
    public synchronized long createNewCallSession(List<UidInfoBean> uidBeans) {
        //当前有正在进行的会话
        if (mCurCallSessionId > 0) {
            ALog.getInstance().e(TAG, "<createNewCallSession> Calling, cannot create new call session.");
            return -1;
        }
        //检测当前是否有用户登录成功
        if (mLocalUser.getUid() <= 0) {
            ALog.getInstance().e(TAG, "<createNewCallSession> No local user login.");
            return -1;
        }
        for (int i = 0; i < uidBeans.size(); i++) {
            mPerRemoteUsers.add(uidBeans.get(i));
        }
        mRemoteUser.clearInfo();    //真正建立的对端暂不确定
        mCurCallSessionId = System.currentTimeMillis() & 0x000000000fffffffL;
        mCurCallChannel = "";
        mCurCallToken = "";
        mCurCallState = CALL_STATE_CALLING;
        return mCurCallSessionId;
    }

    //收到服务器主动呼叫回应，获取channel和token参数
    public synchronized boolean updateNewCallResponse(long sessionId, String channel, String token) {
        //是否有效的session ID
        if (sessionId != mCurCallSessionId) {
            ALog.getInstance().e(TAG, "<createNewCallSession> invalid response session ID: " + sessionId);
            return false;
        }
        mCurCallChannel = channel;
        mCurCallToken = token;
        return true;
    }

    //接到一个呼叫请求，决策处理是否响应这个呼叫会话
    public synchronized boolean receiveCallRequest(long session_id, UidInfoBean remoteUser,
                                      String channel, String token) {
        //当前有正在进行的会话
        if (mCurCallSessionId > 0) {
            ALog.getInstance().e(TAG, "<createNewCallSession> Calling, cannot answer this new call.");
            return false;
        }
        mPerRemoteUsers.clear();
        mRemoteUser.updateInfo(remoteUser);
        mCurCallSessionId = session_id;
        mCurCallChannel = channel;
        mCurCallToken = token;
        mCurCallState = CALL_STATE_CALLING;
        return true;
    }

    //主动挂断或拒绝通话，超时也可以调用
    public synchronized boolean refuseCall() {
        //当前正在进行会话才需要处理
        if (mCurCallSessionId > 0) {
            mPerRemoteUsers.clear();
            mRemoteUser.clearInfo();
            mCurCallSessionId = -1;
            mCurCallChannel = "";
            mCurCallToken = "";
            mCurCallState = CALL_STATE_IDLE;
            return true;
        } else {
            ALog.getInstance().e(TAG, "<createNewCallSession> Not in calling, need not refuse.");
            return false;
        }
    }

    //接听通话，被动呼叫才会出现的操作
    public synchronized boolean answerCall() {
        //当前正在进行会话才能够接听
        if (mCurCallSessionId > 0) {
            mCurCallState = CALL_STATE_TALKING;
            return true;
        } else {
            ALog.getInstance().e(TAG, "<createNewCallSession> Not in calling, can not answer.");
            return false;
        }
    }

    //收到对端忙状态通知
    public synchronized boolean receiveCallBusy(long session_id, long remoteUid) {
        //当前会话和通知会话是否是同一个会话
        if (session_id == mCurCallSessionId) {
            if (mRemoteUser.getUid() == remoteUid) {
                //说明是已接听的主动会话对端，或者是被叫的对端
                mRemoteUser.clearInfo();
                mPerRemoteUsers.clear();
                mCurCallSessionId = -1;
                mCurCallChannel = "";
                mCurCallToken = "";
                mCurCallState = CALL_STATE_IDLE;
            } else {
                //说明是主动一呼多时候尚未建立接听的对端
                for (int i = mPerRemoteUsers.size() - 1; i >= 0; i--) {
                    if (mPerRemoteUsers.get(i).getUid() == remoteUid) {
                        mPerRemoteUsers.remove(i);
                        break;
                    }
                }
                //没有更多等待回复的对端，会话结束
                if (mPerRemoteUsers.size() < 1) {
                    mRemoteUser.clearInfo();
                    mCurCallSessionId = -1;
                    mCurCallChannel = "";
                    mCurCallToken = "";
                    mCurCallState = CALL_STATE_IDLE;
                }
            }
            return true;
        } else {
            ALog.getInstance().e(TAG, "<createNewCallSession> It's not a valid seesionId="
                    + session_id + ", currSessionId=" + mCurCallSessionId);
            return false;
        }
    }

    //收到对端挂断或拒绝通话通知
    public synchronized boolean receiveCallRefuse(long session_id, long remoteUid) {
        //当前会话和通知会话是否是同一个会话
        if (session_id == mCurCallSessionId) {
            if (mRemoteUser.getUid() == remoteUid) {
                //说明是已接听的主动会话对端，或者是被叫的对端
                mRemoteUser.clearInfo();
                mPerRemoteUsers.clear();
                mCurCallSessionId = -1;
                mCurCallChannel = "";
                mCurCallToken = "";
                mCurCallState = CALL_STATE_IDLE;
            } else {
                //说明是主动一呼多时候尚未建立接听的对端
                mPerRemoteUsers.clear();
                mRemoteUser.clearInfo();
                mCurCallSessionId = -1;
                mCurCallChannel = "";
                mCurCallToken = "";
                mCurCallState = CALL_STATE_IDLE;
            }
            return true;
        } else {
            ALog.getInstance().e(TAG, "<createNewCallSession> It's not a valid seesion ID: " + session_id);
            return false;
        }
    }

    //收到对端接听通知
    public synchronized boolean receiverCallAnswer(long session_id, long remoteUid) {
        //当前会话和通知会话是否是同一个会话
        if (session_id == mCurCallSessionId) {
            if (mCurCallChannel.isEmpty()) {
                ALog.getInstance().e(TAG, "<createNewCallSession> Did not got channel and token, cannot join channel.");
                return false;
            }
            //只保留一个有效的remote user，其他的需要主动拒绝
            if (mRemoteUser.getUid() > 0) {
                //说明已经有建立连通的对端了
                ALog.getInstance().e(TAG, "<createNewCallSession> Have remote user workig, cannot answer another.");
                return false;
            }
            for (int i = mPerRemoteUsers.size() - 1; i >= 0; i--) {
                if (mPerRemoteUsers.get(i).getUid() == remoteUid) {
                    mRemoteUser.updateInfo(mPerRemoteUsers.get(i));
                    mPerRemoteUsers.remove(i);
                    mCurCallState = CALL_STATE_TALKING;
                    return true;
                }
            }
            return false;
        } else {
            ALog.getInstance().e(TAG, "<createNewCallSession> It's not a valid seesion ID: " + session_id);
            return false;
        }
    }
}
