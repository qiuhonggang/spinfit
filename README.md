# SpinFit

原生 Android 动感单车训练应用，当前版本 **2.6**。使用 Java 和 Android SDK 构建，无服务端和第三方运行时依赖。

## 功能

- 首页“开始骑行”先选择课程，每次默认自由骑行；有训练进行时直接继续原训练。
- 课程启动按阻力编排决定模式：分段课程默认自动，固定课程开始时设置目标，自由骑行保留当前阻力；不再受旧全局开关影响。
- 通过 BLE 连接动感单车，读取设备提供的功率、踏频、速度、阻力、里程与消耗。
- 阻力直接使用单车刻度，真实单车优先读取设备阻力范围，YESOUL 默认 0–100；不再压缩为 32 档。
- YESOUL M2M 使用实车验证的单字节整数档位控阻；其他 FTMS 设备保留标准编码。控制回执与实际阻力确认分开显示，超时只重试一次；已达到目标后转动车上旋钮会接管为手动。
- 支持标准 FTMS 控阻，按训练阶段设置参考阻力；可手动接管及恢复自动。设备未声明控阻能力时提示手动旋钮。
- 独立连接标准蓝牙心率设备，包括开启标准心率广播的 Apple Watch；心率与单车可分别连接。
- 内置 9 套课程及 4 周入门安排，覆盖自由骑行、入门、有氧、恢复和进阶间歇，新增 30 分钟 HIIT（7 分钟热身 + 6 ×〔1 分钟高强度 / 2 分钟恢复〕+ 5 分钟放松）。
- 手动模式只显示单车实时阻力；加减按钮从实际阻力起调，快速连点保留待执行的目标。自动课程单独标明课程目标。
- 骑行页将阻力控制置于实时数据下方；阶段信息以紧凑进度区呈现，自由骑行不显示无意义的阶段卡。
- 骑行页集中显示踏频、心率、速度、里程、消耗；心率数字随读数从蓝绿向橙红渐变，大字号自动调整排版。
- 本地 SQLite 训练记录，支持速度、里程、心率、功率和踏频曲线，以及滑动查看具体时刻的数据。
- 前台服务按单调时钟记录训练，支持暂停、后台运行、断线暂停及中断记录保留。
- 演示训练与真实训练分开保存，设备未提供的字段保持为空。

## 环境

- Android 8.0 / API 26 及以上。
- JDK 21（已验证的构建环境）。
- Android SDK Platform 35、Build Tools 36.1.0、Platform Tools（实机调试使用）。
- `bash`、`zip` 和 `keytool`。

## 构建

设置 `ANDROID_SDK_ROOT` 指向 Android SDK；未设置时，构建脚本使用 macOS 默认路径 `$HOME/Library/Android/sdk`。

```bash
export ANDROID_SDK_ROOT="/path/to/Android/sdk"
bash app/build.sh
```

输出为 `app/bin/SpinFit.apk`。构建链为 `aapt2 → javac → d8 → zipalign → apksigner`。

首次构建会在本地生成 `app/debug.keystore`。签名文件不提交到 Git。若需要覆盖更新已安装的应用，请保留并使用原来的本地签名文件；使用新签名构建的 APK 无法直接覆盖旧签名版本。

## 测试

纯 Java 核心检查：

```bash
bash tests/run-core.sh
```

Android 实机检查：先构建并安装主应用，再执行：

```bash
adb devices -l
adb -s DEVICE_SERIAL install -r app/bin/SpinFit.apk
bash tests/run-device.sh DEVICE_SERIAL
```

`DeviceChecks` 使用隔离测试数据库，覆盖全部课程的默认控阻、固定阻力启动、手动接管、持久化、数据库迁移、缺失数据和蓝牙报文解析，不修改正式训练记录。

界面验收可单独运行（当前没有真实骑行进行时）：

```bash
adb -s DEVICE_SERIAL shell am instrument -w com.spinfit.bike.tests/com.spinfit.bike.tests.UiChecks
```

`UiChecks` 使用隔离的演示设置和数据库，检查首页课程选择与默认项、继续训练、阻力区域顺序、可见操作按钮、大字号、横屏和小屏，并在结束时恢复字体、旋转、动画及屏幕大小。截图保存在应用外部 `validation` 目录。

`DeviceAudit` 是本地诊断工具，可扫描附近心率广播并备份正式数据库；日常使用和常规测试无需运行。生成的数据库及设备诊断产物不提交到仓库。

## 目录

```text
app/src/com/spinfit/bike/
  app/    连接会话、前台服务、参数与本地记录
  ble/    单车、心率设备及演示数据源
  core/   课程、训练时钟、统计与报文解析
  ui/     页面、图表和视觉组件
app/res/  Android 资源
app/build.sh
tests/   核心与 Android 实机检查
buildtest/ 最初构建链验证的最小示例源码
```

## 数据与使用说明

- 速度、功率等字段来自设备实际提供的数据；不使用模型值冒充实测。演示模式明确标注模拟来源。
- 自动阻力按课程阶段设置参考档位，不是 ERG 功率闭环。HIIT 课程为自编方案，参考 [ACSM 的高强度间歇训练原则](https://www.acsm.org/docs/default-source/files-for-resource-library/high-intensity-interval-training.pdf)，不表示该机构审定了本课程。
- 外部心率优先使用有效的新数据；过期数据不复用。重连先扫描当前广播地址，支持所选手表地址变化；断线和无通知会自动重试三次。若广播端停止或被其他设备占用，需恢复广播后再连接。
- 旧记录已有速度采样时可回补历史均速、最高速度与速度曲线；旧版未保存的逐秒里程不会补造，新训练会保存完整里程采样。
- 训练记录仅保存在本机。正常关闭及同签名覆盖更新会保留记录，卸载应用会删除本地数据。
- 本仓库不包含签名密钥、真实训练数据库、设备抓取文件、第三方 APK 或反编译产物。
