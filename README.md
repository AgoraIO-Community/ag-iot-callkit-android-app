# Agora Call Kit 介绍

*简体中文| [English](README.en.md)*

声网呼叫功能（Agora Call Kit）SDK，针对如智能手表拨号通话、门铃和楼宇对讲呼叫通话、IPC监控实时画面建立连接、会议邀请参会等场景，集成声网RTC实时音视频传输能力，提供呼叫通话一体化综合能力。


# 运行示例项目

声网呼叫功能（Agora Call Kit）提供了一个简单的示例项目，且SDK本身也以开源形式提供，帮助开发者轻松掌握SDK的使用，从而更高效的集成到自己的应用程序中。安卓端示例工程基于Android Studio 4.1.2构建，工程中默认配置的账号信息谨供前期功能验证，相关账号随时可能停用，产品发布前务必按照文档说明申请并配置。

## 1. 准备开发环境

- 安装Android Studio 4.1.2或以上版本。用于构建Demo APK。
- 两台真实 Android 手机设备（开启开发者选项调试权限）。用于安装运行Android Demo。


## 2. 构建Demo APK

1. 运行Android Studio，加载agoracallkit工程。
2. File->Sync Project with Gradle Files，等待Build窗口提示“BUILD SUCCESSFUL”（如果失败，可能需要代理设置以加速相关组件下载）。
3. Build->Make Project，等待Build窗口提示“BUILD SUCCESSFUL”，完成APK的构建。


## 3. 安装Demo APK

1. Android手机开启开发者调试权限后，USB数据线连接电脑，权限弹窗中允许调试，确保Android Studio操作界面右上方出现手机设备名称，连接调试设备成功。
2. Android Studio界面点击“运行”按钮，自动安装APK并调试。
3. 按以上步骤，在另一台Android手机上安装同一APK。


## 4. 运行程序

1. 在启动界面之后，输入一个登录的账户名称，点击"登录"按钮直接登录
2. 登录成功后会显示当前账号的 '账号Id'，这是SDK内部生成的账号唯一标识
3. 输入对端的 '账号Id'之后，可以直接呼叫对端，呼叫成功后，进入通话界面
4. 通话界面可以显示实时网络状态、截屏、音效等处理
5. 在 "我的"界面中，可以登出当前账号
