# 回话：Android 三选一回复助手（0.2.2-alpha）

QQ 文字通知已在一加 13、Android 16、QQ 9.3.50 上初步验证可读（首轮 5/5）；用户已在该设备上验证 DeepSeek Flash 生成流程可用。通知没有可靠联系人 ID，首版只按用户当前选择的关系生成，不跨通知复用联系人档案。微信 8.0.71 尚未测试，不能宣称兼容。本项目与腾讯、DeepSeek 无官方关联。

## 已实现

- QQ / 微信通知独立开关、系统通知使用权诊断、最近 50 条 / 10 分钟临时记录。
- 从记录页挑选一条文字通知，或手动粘贴；选择本次关系、填写自己的想法，调用自行配置的 Chat Completions 兼容模型生成恰好三条回复。
- 模型地址必须使用 HTTPS；API Key 由 Android Keystore 加密保存。云端生成默认关闭，只有开启后才会上传当前消息与选定关系；自动处理 QQ 新通知还需另开开关和本应用通知权限。
- 建议只在内存保留 10 分钟。点选复制后，由用户回到 QQ 自行核对和发送；应用不代发消息。

自动处理 QQ 通知时，因为来源身份尚未可靠确认，只使用当前来信和“未知关系”，不附加跨通知历史。可能收到群聊通知；用户须在使用前确认。当前不支持图片 / 语音内容、自动识别关系、已发送状态确认或微信自动生成。

## 构建与安装

需要 JDK 17+、Android SDK Platform 36 与 Build Tools 35。Windows 项目路径含中文，请用 [构建脚本](tools/build.ps1)：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\build.ps1
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

APK 位于 `android/app/build/outputs/apk/debug/app-debug.apk`，使用开发调试签名，仅供个人试用。

## 在手机上使用

1. 打开“回话”，在“接入”页开启 QQ 和“开始接入记录”；微信保留关闭，直到单独验证。
2. 在“设置”页填写兼容 Chat Completions 的完整 HTTPS 接口地址、精确模型 ID，并把自己的 API Key 直接输入手机。不要把 Key 发到聊天或提交到仓库。
3. 阅读域名和上传说明后，开启“同意云端生成”。先在“生成”页用虚构文本测试自己的服务商配置。
4. QQ 收到文字通知后，在“记录”页选取该条，确认来源及本次关系，点击“生成三条回复”。如需新通知直接生成，再于设置页开启“QQ 新通知自动生成”并授权本应用发送通知。
5. 查看三条回复，复制合适的一条，回到 QQ 自行核对并发送。关闭云端生成会取消当前请求；关闭接入会停止新通知处理。

当前接口仅支持常见的 `model`、`messages` 和 `choices[0].message.content` 文本格式。不同服务商可能需要独立适配；401、429、超时和无效三条回复会在应用内提示。每日最多发起 100 次请求。

若使用 DeepSeek，本应用需要完整接口地址 `https://api.deepseek.com/chat/completions`，而不是 SDK 示例里的基址。设置页在检测到 DeepSeek 地址路径不完整时会显示“使用 DeepSeek 官方接口”按钮；请点击后再用虚构消息重试。新版会将余额不足、路径错误、参数错误与服务端故障分别提示。

若提示超时，设置页可一键切换到 `deepseek-flash`，同时补全官方接口地址并保留本机已加密保存的 API Key。DeepSeek 请求会关闭思考模式并要求 JSON 输出，以便更快得到三条短回复。连接超时、等待模型响应超时和其他网络错误会分别提示；单次生成最长等待约 45 秒。切换型号后先用虚构消息测试，实际速度和可用性取决于服务商。

真机兼容性见 [记录](docs/compatibility-matrix.md)，数据处理说明见 [隐私说明](PRIVACY.md)，实施进展见 [执行状态](docs/execution-status.md)。源码按 [MIT License](LICENSE) 开放。
