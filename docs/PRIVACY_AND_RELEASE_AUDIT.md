# 只读隐私与发布配置核验

核验日期：2026-10-07。本阶段未发布、部署、推送远程或操作 Play Console。

## 签名与商店素材

- `tools/VerifyLocalSigning.java` 只读载入现有配置，确认所有必要属性存在、私钥可读取、证书有效；执行前后 keystore SHA-256 一致。
- `keystore.properties`、`keystore/` 均被 Git 忽略。未更换或重建发布签名身份，未输出密码或私钥。
- release applicationId 仍为 `com.yishenghuang.sealrec`。debug 使用 `.debug` 后缀，避免本地测试覆盖已安装正式版及其数据。
- 素材存在且可解码：`sealrec-app-icon-512.png` 为 512×512；`sealrec-feature-graphic-1024x500.png` 为 1024×500；`sealrec-feature-banner.png` 为 1536×1024，属于原始素材而非尺寸相同的正式横幅。
- 正式横幅仍含旧版 UI 和 “Hardware-key sealed” 表述。需要将来发布前同步最新截图和谨慎的设备密钥文案。本阶段保留原素材，没有发布。
- 本地配置有效不等于已确认 Play 接受该上传证书；未访问生产控制台核实远端签名状态。

## 隐私网站（仅核查）

应用 URL 为 https://sealrec-privacy.netlify.app/ ，HTTP GET 返回 200，包含正确联系邮箱 `sealrec@fastmail.com`。
同时只读检查了 `C:\Users\yishe\Documents\GitHub\sealrec-privacy\index.html`；没有修改或部署该仓库。

网站后续需要同步的事项：

1. 硬件支持随设备而异，Android Keystore 的软件实现不能被描述为保证硬件支持。建议使用“设备 Keystore 密钥；在设备支持时硬件保护”。
2. 签名证明 PCM 哈希与嵌入证书公钥的签名相符；不认证人的身份、某一物理设备或厂商。
3. 当前 v1 格式未签音频格式、设备时间；时间不可信。恢复音频在恢复时签名，不能声称保留中断前的完整真实性证据。
4. WAV 是明文，封装签名不等于加密；导出/分享后的副本由接收应用/用户自行管理。
5. 云备份与设备迁移显式排除私有录音、数据库、偏好；分享通过用户选定的接收应用及临时只读内容 URI。建议网站补充这些边界。

## 官方依据

- [音频输入共享](https://developer.android.com/media/platform/sharing-audio-input)：输入被系统静音与输出音频焦点是不同机制；需要同时处理。
- [音频焦点](https://developer.android.com/media/optimize/audio-focus)：新系统的焦点资格与前台状态有关。
- [麦克风前台服务](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone)：用户授权、服务类型与后台限制。
- [Android 备份](https://developer.android.com/identity/data/autobackup)：仅 allowBackup=false 在部分设备上不能保证禁止设备间迁移，因此增加显式排除规则。
