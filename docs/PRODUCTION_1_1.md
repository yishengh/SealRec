# SealRec 1.1 (2) 生产发布记录

日期：2026-10-07（America/New_York）。这是用户在本地完善阶段完成后另行授权的生产发布；此前文档中的“不发布”描述属于上一阶段。

## 源码与产物

- 发布源码提交：`1b40d00ce045bd9ee01ffd450ba426aa20043cb8`。
- 用户最初要求 main，已创建并推送；随后改为 master，已将同一提交快进推送到 `origin/master`。保留 main，没有删除远程分支。
- 包名 `com.yishenghuang.sealrec`，versionName `1.1`，versionCode `2`，minSdk 26，targetSdk 36。
- AAB：`app/build/outputs/bundle/release/app-release.aab`。
- AAB SHA-256：`592e9b092c8b1d6bf44b1ea4d6cb8ffe9efed80b946a0819bcbd5a0fa1edf431`。
- APK：`app/build/outputs/apk/release/app-release.apk`。
- APK SHA-256：`572c648f684afb043a35e09a569a0ad4854feeb252c714b0026ee6e85dc5f636`。
- 保留既有发布签名；APK 证书 SHA-256：`7eb302fda7fd252a125a5bc39334b1868b4e7b71b517b46a06e897d73358db14`。

## 验证

`gradlew.bat :app:bundleRelease :app:assembleRelease :app:testDebugUnitTest :app:lintRelease --offline --no-daemon --max-workers=1 --console=plain` 成功，见 [构建日志](validation/production-build.txt)。当前插件未生成 `testReleaseUnitTest`，因此使用项目实际可用的 JVM 单元测试任务。

- 15 项 JVM 单元测试通过；release lint 0 错误、32 警告。
- apksigner 验证通过，包名/版本/SDK 与预期一致；Play 接受 AAB 的签名和版本。
- 正式 APK 在项目专用、禁用宿主音频的 API 26 模拟器安装成功，MainActivity 启动返回 `Status: ok`，应用进程存活，未发现应用崩溃。模拟器已关闭，没有覆盖用户正式设备上的录音。
- 先前完整核心功能验证见 [本地验证报告](LOCAL_COMPLETION.md)；真机及长时间实时录音的限制仍适用。

## Google Play 状态

已在 SealRec 的 Production 轨道上传并保存 `1.1 (2)`，提交三个更改：版本全量发布、177 个国家/地区、其余地区。覆盖范围沿用此前封闭测试的全部 178 个地区。

提交后 Publishing overview 显示 **Changes in review**，仍在运行 quick checks；成功完成预检查后进入 Google 审核。**Managed publishing off**，审核通过后自动发布。本记录不代表商店已上线或审核已通过。

Play 保留两条非阻断警告：没有混淆映射文件（本项目未启用混淆）、依赖所带原生库没有上传调试符号。无发布阻断错误。

送审截图保存在本地 `build/production-submitted.jpg`；未把账户控制台截图提交到远程仓库。没有修改隐私网站、发布签名身份或其他应用。

## en-US 发布说明

Improved recording, pause/resume, saving and playback reliability.
Recover interrupted recordings and handle low storage more safely.
Improved rename, share, export and recycle bin behavior.
Better permission guidance, dark theme, accessibility labels and offline language switching.
Record and verify locally without an internet connection.
