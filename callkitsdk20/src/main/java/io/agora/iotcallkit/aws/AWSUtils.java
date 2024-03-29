package io.agora.iotcallkit.aws;

import android.content.Context;
import android.util.Log;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttNewMessageCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttSubscriptionStatusCallback;
import com.amazonaws.regions.Regions;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * AWS MQTT操作能力封装，仅保留需要使用的设备影子能力
 */
public class AWSUtils {
    private static final String TAG = "IOTSDK/AWSUtils";

    private AWSIotMqttManager mqttManager;
    CognitoCachingCredentialsProvider mCredentialsProvider;
    private String mClientId;
    private String mUserInventThingName;
    private int mTopicSum = 0;


    private AWSUtils() {
    }

    /* 获取单例对象 */
    public static AWSUtils getInstance() {
        return AWSUtils.SingletonFactory.instance;
    }

    /* 此处使用一个内部类来维护单例 */
    private static class SingletonFactory {
        private static AWSUtils instance = new AWSUtils();
    }

    /* 连接AWS MQTT服务 */
    private void connect(Context context, final String identityId, String token,
                         String accountId, String identityPoolId, String mRegion,
                         String thingName, final AWSListener awsListener) {
        //记录当前登录用户的虚拟设备thing name
        mUserInventThingName = thingName;
        mClientId = identityId;
        try {
            //AWS服务用户身份
            AWSDeveloperIdentityProvider developerProvider = new AWSDeveloperIdentityProvider(
                    identityId, token, accountId, identityPoolId, mRegion);
            //AWS服务证书
            mCredentialsProvider = new CognitoCachingCredentialsProvider(context, developerProvider,
                    Regions.fromName(mRegion));
            //携带AWS服务证书连接AWS IoT Core
            mqttManager.connect(mCredentialsProvider, new AWSIotMqttClientStatusCallback() {
                @Override
                public void onStatusChanged(final AWSIotMqttClientStatus status,
                                            final Throwable throwable) {
                    Log.d(TAG, "Status = " + String.valueOf(status));
                    //连接成功订阅所需topic
                    if (String.valueOf(status).equals("Connected")) {
                        subscribe(mClientId, mUserInventThingName);
                    }
                    //通知连接状态事件
                    if (awsListener != null) {
                        awsListener.onConnectStatusChange(status.toString());
                    }
                }
            });
        } catch (final Exception e) {
            Log.e(TAG, "Connection error.", e);
            //通知连接失败事件
            if (awsListener != null) {
                awsListener.onConnectFail(e.getMessage());
            }
        }
    }

    /**
     * 断开MQTT连接
     */
    public void disConnect() {
        try {
            //断开MQTT链接
            mqttManager.disconnect();
            //销毁AWS服务证书
            mCredentialsProvider.clear();
        } catch (Exception e) {
            Log.v(TAG, "disConnect,exception=" + e.getMessage());
        }
    }

    /* 订阅所需的MQTT topic */
    private void subscribe(String clientId, String inventDevciceName) {
        final String topic = "+/+/device/connect";                  //设备上下线通知
        final String topic2 = "$aws/things/+/shadow/get/+";         //获取设备影子内容
        final String topic3 = "$aws/things/" + inventDevciceName + "/shadow/name/rtc/update/accepted";   //APP影子更新通知
        final String topic4 = "$aws/things/" + inventDevciceName + "/shadow/name/rtc/get/accepted";      //APP影子当前影子内容
        final String topic5 = "granwin/" + clientId + "/message";   //广云服务通知，包含设备上下线，设备上报信息、绑定列表刷新信息
        try {
            mTopicSum = 5;
            //订阅设备在线状态通知topic
            subscribeTopic(topic, AWSIotMqttQos.QOS0);
            //订阅设备影子更新通知topic
            subscribeTopic(topic2, AWSIotMqttQos.QOS0);
            //订阅设备影子更新通知topic
            subscribeTopic(topic3, AWSIotMqttQos.QOS1);
            //订阅设备影子内容topic
            subscribeTopic(topic4, AWSIotMqttQos.QOS1);
            //订阅广云服务通知topic
            subscribeTopic(topic5, AWSIotMqttQos.QOS0);
        } catch (Exception e) {
            Log.e(TAG, "Subscription Error", e);
        }
    }

    /* 订阅topic */
    private void subscribeTopic(String topic, AWSIotMqttQos qos) {
        mqttManager.subscribeToTopic(topic, qos,
                new AWSIotMqttSubscriptionStatusCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Subscribe Shadow Success,topic=" + topic);
                        mTopicSum -= 1;
                        if (mTopicSum == 0) {
                            awsListener.onConnectStatusChange("Subscribed");
                        }
                    }
                    @Override
                    public void onFailure(Throwable exception) {
                        Log.v(TAG, "Subscribe Shadow Error,topic=" + topic, exception);
                    }
                },
                new AWSIotMqttNewMessageCallback() {
                    @Override
                    public void onMessageArrived(final String topic, final byte[] data) {
                        try {
                            String payload = new String(data, "UTF-8");
                            handleMessage(topic, payload);
                        } catch (UnsupportedEncodingException e) {
                            Log.e(TAG, "payload to string error", e);
                        }
                    }
                });
    }

    /* 解析MQTT消息 */
    private void handleMessage(String topic, String payload) {
        try {
            Log.d(TAG, "<handleMessage> " + topic + " payload=" + payload);
            JSONObject jsonMessage = new JSONObject(payload);
            if (topic.contains("/device/connect")) {
                //设备上下线
                JSONObject dataObject = jsonMessage.getJSONObject("data");
                boolean isOnline = dataObject.getBoolean("connect");
                String deviceMac = parseDevMac(topic);
                awsListener.onDevOnlineChanged(deviceMac, "", isOnline);

            } else if (topic.contains("/shadow/get/accepted")) {
                //只关注设备端上报的当前状态
                JSONObject desiredObject = jsonMessage.getJSONObject("state").getJSONObject("reported");
                //去除topic前后内容，获取设备唯一标志
                String things_name = topic.replaceAll("\\$aws/things/", "");
                things_name = things_name.replaceAll("/shadow/get/accepted", "");
                //通知收到设备状态更新事件
                awsListener.onReceiveShadow(things_name, desiredObject);

            } else if (topic.contains("/shadow/name/rtc/update/accepted")) {
                //只关注APP端影子的期望值
                if (jsonMessage.getJSONObject("state").has("desired")
                    && !jsonMessage.getJSONObject("state").isNull("desired")) {
                    JSONObject desiredObject = jsonMessage.getJSONObject("state").getJSONObject("desired");
                    //去除topic前后内容，获取设备唯一标志
                    String things_name = topic.replaceAll("\\$aws/things/", "");
                    things_name = things_name.replaceAll("/shadow/name/rtc/update/accepted", "");
                    //通知收到APP端控制事件
                    if (things_name.equals(mUserInventThingName)) {
                        awsListener.onUpdateRtcStatus(desiredObject);
                    }
                }

            } else if (topic.contains("/shadow/name/rtc/get/accepted")) {
                //只关注APP端影子的期望值
                if (jsonMessage.getJSONObject("state").has("desired")
                    && !jsonMessage.getJSONObject("state").isNull("desired")) {
                    JSONObject desiredObject = jsonMessage.getJSONObject("state").getJSONObject("desired");
                    //去除topic前后内容，获取设备唯一标志
                    String things_name = topic.replaceAll("\\$aws/things/", "");
                    things_name = things_name.replaceAll("/shadow/name/rtc/get/accepted", "");
                    //通知收到APP端控制事件
                    if (things_name.equals(mUserInventThingName)) {
                        awsListener.onUpdateRtcStatus(desiredObject);
                    }
                }

            }  else if (topic.contains("granwin/")) {
                //广云服务消息通知
                if (jsonMessage.has("messageType")
                    && jsonMessage.has("data")) {
                    int messageType = jsonMessage.getInt("messageType");
                    JSONObject data = jsonMessage.getJSONObject("data");
                    String deviceMac = "";
                    long deviceId = 0;
                    Iterator iterator = null;
                    String key = "";
                    switch (messageType) {
                        case 1:         //设备上下线（目前测试效果看通知是比较及时的，秒级延迟）
                            deviceMac = jsonMessage.getString("mac");
                            deviceId = jsonMessage.getLong("deviceId");
                            boolean isOnline = data.getBoolean("connect");
                            //awsListener.onDevOnlineChanged(deviceMac, String.valueOf(deviceId), isOnline);
                            break;

                        case 2:         //设备属性点上报（目前测试不是很稳定，不能保证变更参数都有通知到，可以不关注）
                            deviceMac = jsonMessage.getString("mac");
                            deviceId = jsonMessage.getLong("deviceId");
                            iterator = data.keys();
                            Map<String, Object> properties = new HashMap<>();
                            while(iterator.hasNext()){
                                key = (String) iterator.next();
                                Object value = data.get(key);
                                properties.put(key, value);
                            }
                            awsListener.onDevPropertyUpdated(deviceMac, String.valueOf(deviceId),
                                                properties);
                            break;

                        case 3:         //绑定设备列表更新（目前测试效果看通知是比较及时的，秒级延迟，可以用于设备添加成功的通知）
                            long userID = jsonMessage.getLong("userId");
                            iterator = data.keys();
                            while(iterator.hasNext()){
                                key = (String) iterator.next();
                                JSONObject dev_info = data.getJSONObject(key);
                                deviceMac = dev_info.getString("mac");
                                String actionType = dev_info.getString("actionType");
                                awsListener.onDevActionUpdated(deviceMac, actionType);
                            }
                            break;
                        default:
                            break;
                    }
                }
            } else {
                    //未知的消息
            }

        } catch (Exception ex) {
            Log.e(TAG, "parse payload error", ex);
        }
    }

    /*
     * @brief 解析用"/"分隔的第二个字段: MAC地址
     */
    String parseDevMac(String sourceText) {
        StringTokenizer st = new StringTokenizer(sourceText, "/");
        int index = 0;

        while(st.hasMoreElements()) {
            String segment = st.nextToken();
            if (index == 1) {
                return segment;
            }
            index++;
        }

        return "";
    }

    /**
     * 获取设备当前状态
     * things_name：设备唯一标志码
     */
    public void getDeviceStatus(String things_name) {
        String topic = "$aws/things/" + things_name + "/shadow/get";
        try {
            //主动查询设备影子，结果会反馈到"$aws/things/+/shadow/get/+"订阅topic中
            mqttManager.publishString("", topic, AWSIotMqttQos.QOS0);
        } catch (Exception e) {
            Log.e(TAG, "Publish error.", e);
        }
        Log.i(TAG, "message sent: " + topic);
    }

    /**
     * 远程控制，设置设备状态
     * account：APP用户身份标志，来源于AWS身份池
     * productKey：设备的产品key，来源于广云平台
     * things_name：设备唯一标志
     * params：需要设置的属性键值对
     */
    public void setDeviceStatus(String account, String productKey, String things_name,
                                Map<String, Object> params) {
        String topic = "$aws/things/" + things_name + "/shadow/update";
        try {
            //构建设备影子属性期望值
            JSONObject desiredValue = new JSONObject();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                desiredValue.put(entry.getKey(), entry.getValue());
            }
            JSONObject desiredUserControlValue = new JSONObject();
            desiredUserControlValue.put("product_key", productKey);            //产品kek
            desiredUserControlValue.put("action_type", "1");            //控制端类型
            desiredUserControlValue.put("action_type_name", "android"); //控制端类型名
            desiredUserControlValue.put("account", account);                   //APP用户身份标志
            desiredValue.put("userControllerData", desiredUserControlValue);
            //构建update消息体
            JSONObject stateValue = new JSONObject();
            stateValue.put("desired", desiredValue);
            JSONObject messageValue = new JSONObject();
            messageValue.put("state", stateValue);
            //发送update消息体，设备端会收到"$aws/things/${things_name}/shadow/update/accepted"通知
            Log.i(TAG, topic + "->" + messageValue.toString());
            mqttManager.publishString(messageValue.toString(), topic, AWSIotMqttQos.QOS1);
        } catch (Exception ex) {
            Log.e(TAG, "create payload error", ex);
        }
    }

    /**
     * 主动查询APP影子的状态
     */
    public void getRtcStatus() {
        String topic = "$aws/things/" + mUserInventThingName + "/shadow/name/rtc/get";
        try {
            //主动查询设备影子，结果会反馈到"$aws/things/+/shadow/name/rtc/get/+"订阅topic中
            mqttManager.publishString("", topic, AWSIotMqttQos.QOS1);
        } catch (Exception e) {
            Log.e(TAG, "Publish error.", e);
        }
        Log.i(TAG, "message sent: " + topic);
    }

    /**
     * 更新APP设备当前状态（到APP影子）
     * params：需要设置的属性键值对
     */
    public void updateRtcStatus(Map<String, Object> params) {
        String topic = "$aws/things/" + mUserInventThingName + "/shadow/name/rtc/update";
        if (!params.isEmpty()) {
            try {
                //构建设备影子属性值
                JSONObject reportedValue = new JSONObject();
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    reportedValue.put(entry.getKey(), entry.getValue());
                }
                //构建update消息体
                JSONObject stateValue = new JSONObject();
                stateValue.put("reported", reportedValue);
                JSONObject messageValue = new JSONObject();
                messageValue.put("state", stateValue);
                //发送update消息体，更新结果会反馈到订阅的“"$aws/things/" + clientId + "/shadow/update/accepted"” topic中
                Log.i(TAG, topic + "->" + messageValue.toString());
                mqttManager.publishString(messageValue.toString(), topic, AWSIotMqttQos.QOS1);
            } catch (Exception ex) {
                Log.e(TAG, "create payload error", ex);
            }
        }
    }

    /**
     * context：应用上下文，来源于activity对象
     * clientID：用户身份ID，来源于AWS用户池
     * mCustomerSpecificEndpoint：AWS服务集群节点
     * token：用户身份token，来源于AWS用户池
     * accountId：用户身份标志，来源于AWS身份池
     * identityPoolId：用户身份池ID，来源于AWS身份池
     * mRegion：AWS服务所在区域
     */
    public void initIoTClient(Context context, String clientID, String mCustomerSpecificEndpoint,
                              String token, String accountId, String identityPoolId, String mRegion,
                              String thingName) {
        // MQTT Client
        mqttManager = new AWSIotMqttManager(clientID, mCustomerSpecificEndpoint);
        // Set keepalive to 10 seconds.  Will recognize disconnects more quickly but will also send
        // MQTT pings every 10 seconds.
        mqttManager.setKeepAlive(10);
        // connect to AWS service
        connect(context, clientID, token, accountId, identityPoolId, mRegion, thingName, awsListener);
    }

    /**
     * 设置AWS服务状态监听者
     * awsListener：事件监听者对象
     */
    public void setAWSListener(AWSListener awsListener) {
        this.awsListener = awsListener;
    }

    private AWSListener awsListener;

    /**
     * AWS事件监听接口对象
     */
    public interface AWSListener {
        void onConnectStatusChange(String status);                      //连接状态改变事件
        void onConnectFail(String message);                             //连接失败
        void onReceiveShadow(String things_name, JSONObject jsonObject);//设备状态更新事件
        void onUpdateRtcStatus(JSONObject jsonObject);                     //APP状态控制事件
        void onDevOnlineChanged(String deviceMac, String deviceId, boolean online);  // 设备上下线事件
        void onDevActionUpdated(String deviceMac, String actionType);    // 绑定设备列表刷新

        // 设备属性更新
        void onDevPropertyUpdated(String deviceMac, String deviceId, Map<String, Object> properties);

    }
}

