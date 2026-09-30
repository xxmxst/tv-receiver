# TV Receiver

Android TV / Android 电视投屏接收端：
- 局域网发布可发现服务（NSD/mDNS）
- 接收手机端发送的视频地址并播放
- 支持遥控器：暂停/继续、快进快退、倍速菜单
- 支持全屏播放（返回键退出全屏）
- 支持手机端远程控制（`/control`）
- `/cast` 支持可选 MIME、标题和网页请求头，`/status` 提供播放状态
- 修复普通 Android 启动器入口，安装后可直接显示应用图标
- 启动阶段增加容错，服务初始化失败时仍可进入主页面查看状态

## 远程控制接口
- `POST /cast`
  - body: `{ "url": "..." }`
  - 作用：开始播放指定视频链接

- `POST /control`
  - body: `{ "action": "play|pause|stop" }`
  - 作用：播放控制（继续/暂停/退出播放）

## 环境要求
- Android Studio
- JDK 17
- Android SDK 34

## 运行
```bash
./gradlew assembleDebug
```

## Release APK
路径：
- `app/build/outputs/apk/release/app-release.apk`

## v1.1.1
- 修复 `MainActivity` 启动入口，兼容 `LAUNCHER` 与 `LEANBACK_LAUNCHER`
- 修复部分设备安装后桌面不显示应用的问题
- 修复启动时因服务初始化异常导致的一闪而退

## 配套项目
手机发送端仓库：
- `https://github.com/qq244901796/mobile-sender`
