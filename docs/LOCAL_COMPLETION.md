# SealRec 本地完善清单（2026-10-07）

## 边界与恢复的产品决策

基线 c8fcc78，开始时工作区干净；未发现项目/父目录 AGENTS.md 或项目规则文件。
已阅读 README、构建配置、Manifest、核心代码及 Cursor 主历史用户决策。
定位：离线 PCM 录音、设备密钥签名、WAV 内嵌 seal、离线完整性校验；不是内容真实性、可信时间或录音加密证明。
历史确认：播放离开库/后台停止；录音与播放互锁；多语言、主题、质量、通知声音选项、回收站、核验 JSON 已在范围内。
本次不增加云服务、转写、可信时间戳、分段签名等新方向，不发布/部署/推送。

## 有限工作项

- [x] 录音命令串行化，启动/暂停/恢复/停止/保存错误收敛、资源释放，焦点中断后用户手动恢复。
- [x] 流式封装/校验/恢复、原始格式持久化、准确时长、低存储与长度上限，保留失败录音。
- [x] 严格校验异常 WAV，限制内存分配，保护导入缓存和私有文件操作。
- [x] 播放暂停/恢复/拖动/结束、焦点与生命周期；重命名、导出/分享、回收站一致性。
- [x] 权限拒绝、空状态、错误恢复、无障碍与所有现有语言资源一致性。
- [x] 只读核验签名配置/素材及隐私网站；记录需以后处理的站点问题。
- [x] debug 构建、单元测试、lint、可用模拟器上的测试音频核心流程；记录未验证真机行为。

## 已发现的基线缺陷

WAV 封装/校验/恢复整文件分配；解析器信任 chunk 长度；恢复后读取已删除 raw 长度导致时长零；恢复固定 44.1kHz；秒级 WAV 文件名碰撞；服务异步启动及 stop/cancel 竞态；录音焦点回调空实现；导入在主线程复制且不清理；部分文件/播放异常可逃逸。

## 完成内容

1. 服务通过命令队列串行执行，重复开始/停止安全；录音和播放互锁。以实际 PCM 长度计时；焦点丢失、输入静音或读失败时暂停，必须由用户恢复。前台服务与录音期间的部分唤醒锁支持后台/锁屏，停止后释放资源。
2. 录音、WAV 写入、校验、恢复均采用有界缓冲。原始文件持久化采样格式，保存采用临时文件及原子重命名，数据库确认后才删除 raw。保存日志覆盖 WAV 已完成、数据库已登记、临时副本未清理等中断点，避免重复登记。保留低空间/保存失败后的原始数据；完整帧可从尾部不完整的 raw 中恢复。
3. 检查 RIFF/chunk 长度、重复块、格式、数据对齐和 seal 上限；拒绝畸形文件。导入采用 IO 线程、有界复制、临时缓存清理与取消处理。录音上限为 2,000,000,000 字节，保存预留副本空间及 8 MiB 余量。
4. 流式播放修复暂停/恢复/拖动/结束重播和资源竞争。重命名检查非法名称、大小写冲突（包括回收站），失败回滚；分享仅授权私有录音目录中的 WAV/JSON。MediaStore 导出处理失败清理、旧系统存储授权及路径，重复导出不覆盖已有文件。永久删除/清空回收站与丢弃未完成录音均需界面确认。
5. 修复麦克风/通知权限回调；旧版导出授权拒绝有错误反馈。恢复/保存时禁用冲突操作。空状态、深色主题、窄屏滚动、操作按钮换行、辅助描述和滑块标签一致化。全部 10 组语言资源各有 94 个 string，键集合相同；离线切换不依赖下载语言包。
6. 显式排除私有数据的备份/迁移；继续无网络权限。debug 包独立为 `com.yishenghuang.sealrec.debug`。签名、商店素材及隐私网站只读核验结果见 [审计](PRIVACY_AND_RELEASE_AUDIT.md)。

## 本地验证证据

最终构建与模拟器证据在 [validation](validation/) 中；下面的模拟器测试均使用测试数据，AudioRecord 流程仅在以 `-no-audio` 启动的专用模拟器运行，未采集宿主环境音频。

| 最终检查 | 结果 | 证据 |
|---|---|---|
| debug APK / instrumentation APK / 单元测试 / lint | BUILD SUCCESSFUL | [构建日志](validation/build.txt) |
| JVM 单元测试 | 15 通过，0 失败 | [测试汇总](validation/unit-tests.txt) |
| lintDebug | 0 错误，38 警告 | [lint 汇总](validation/lint-summary.txt) |
| API 26 完整集成测试 | 20 执行通过，2 条件跳过；runner 显示 OK (22 tests) | [API 26](validation/api26-tests.txt) |
| API 37 完整集成测试 | 20 执行通过，2 条件跳过；runner 显示 OK (22 tests) | [API 37](validation/api37-tests.txt) |
| API 26 跨进程中断恢复 | prepare / force-stop / recover 通过 | [prepare](validation/process-prepare-api26.txt)、[recover](validation/process-recover-api26.txt) |
| API 37 跨进程中断恢复 | 系统稳定后 prepare / force-stop / recover 通过 | [prepare](validation/process-prepare.txt)、[recover](validation/process-recover.txt) |

- 构建命令：`gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --offline --no-daemon --max-workers=1 --console=plain`。
- 单元测试 15 项：WAV/seal 正常与异常输入、拒绝覆盖、完整性校验、恢复格式、时长及 RMS。两小时 16 kHz 单声道合成 PCM（230.4 MB）在测试 JVM 128 MiB 堆限制下完成流式写入和哈希；这不是两小时实时硬件录音测试。
- 核心集成：三种采样率、开始/暂停/恢复/停止/保存、重复命令、音频焦点中断、锁屏录音、播放/拖动/结束重播、重命名、内容 URI 可读、重复导出 WAV+JSON、回收站恢复/永久删除。
- 故障注入：输入错误、权限异常、低空间、长度上限、保存失败保留 raw、不完整帧恢复，以及 WAV 完成/数据库提交/临时文件残留的恢复路径。
- 真实跨进程恢复：分开执行 prepare/recover instrumentation，中间 `am force-stop`，恢复 16 kHz 合成数据，核对格式、时长、签名和源文件清理。常规测试中这两个用例按条件跳过，不能将 runner 总数全部算作执行数。
- 界面：手机与平板宽度、130% 字体、深色主题、空库/空回收站、中英文切换、设置/关于/返回导航；截图见 [手机](validation/dark-phone.png)、[平板](validation/dark-tablet.png)。
- API 37 手动权限流程：拒绝麦克风后显示设置指引且不启动录音；麦克风已授权、仅通知被拒绝时仍可录音并正常停止。
- lint 无错误；保留的 38 条警告主要为依赖/目标 SDK 升级提示、未使用资源、KTX 建议、冗余版本判断、磁盘可用空间估计等。没有批量屏蔽检查。`usableSpace` 只是提前预警，实际写入失败仍单独处理并保留 raw。
- 发布密钥只读校验通过，前后 keystore SHA-256 一致；未构建/上传发布包，未修改发布签名身份。

API 37 补充进程测试曾在准备阶段被模拟器低内存回收器终止，随后 recover 因没有测试样本而失败。没有将该次运行计为通过；等待系统启动稳定后，同一 APK、同一测试成功完成两个阶段。保留 [系统杀进程记录](validation/api37-process-memory-pressure.txt)、[失败 prepare](validation/api37-process-prepare-attempt.txt)、[失败 recover](validation/api37-process-recover-attempt.txt)，以区分测试环境失败与已通过的恢复验证。

运行环境：Android Studio JBR、项目 Gradle 9.6.1 / AGP 9.3.1；依赖已本地解析，最终验证使用 offline 模式。API 26 为项目内创建的 `SealRecApi26`（端口 5678），API 37 为 `Pixel_10_Pro` 的只读测试实例；仅使用明确指定的 adb serial，没有操作其他项目的模拟器。

最终 debug APK：`app/build/outputs/apk/debug/app-debug.apk`，SHA-256 为 `c5bc0b3e770ad06b0815e4ebb67c8df090b4d9f3109a402956e915f235043e29`。这是本地调试包，不是发布包。

## 明确未验证及后续依赖

- 真机运营商来电、蓝牙/USB 路由、厂商电池策略/后台杀进程、物理设备 StrongBox 行为需要对应设备。已验证模拟器音频焦点及进程中断，不能替代这些真机条件。
- 数小时实时录音的发热、电量、系统稳定性尚未验证；长文件验证是合成数据。低磁盘空间为注入测试，未填满用户磁盘。
- 已验证分享 URI 和导出文件内容，没有向外部接收应用实际发送录音；完整 TalkBack 人工走查、各语言母语审校尚未完成。
- 没有格式旁文件的旧 raw 无法可靠推断历史采样率，按旧默认 44.1 kHz 恢复。WAV 不加密；v1 签名仅保护 PCM 哈希，不保护格式、时间或人的身份，恢复会重新签名。
- 隐私网站文案及商店图需未来发布前更新；修改建议只保存在本项目。本次未修改/部署网站、推送 Git、上传 Play 或触发远程发布。
- 本阶段不升级整个依赖栈、不扩展产品方向。发布资格、商店签名匹配及真机验收不作为已完成事项。
