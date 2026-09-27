# 模型接入与评测状态

状态：用户已在手机上配置 DeepSeek 官方完整接口与 Flash 模型，并反馈 0.2.2-alpha 功能可用。应用不读取或输出 API Key。旧版曾因接口路径与短超时导致失败；新版已提供官方接口一键填写、超时调整和错误分类。输出质量及端到端时延的系统评测尚未完成。

当前客户端按常见 Chat Completions 形状发送 `model` 与 `messages`，从 `choices[0].message.content` 读取 JSON。地址必须是完整 HTTPS URL；认证采用 `Authorization: Bearer <Key>`，不跟随重定向。不是所有“兼容”服务都严格使用这一格式，配置时须以所选服务商的实际文档和虚构消息测试为准。

DeepSeek 官方文档给出的请求路径是 `POST /chat/completions`，配合 `https://api.deepseek.com` 基址；在本应用应填写完整地址 `https://api.deepseek.com/chat/completions`。官方错误码说明区分 400 请求格式错误、402 余额不足、422 参数错误和 500 / 503 服务端问题；客户端另将 404 和重定向归为接口路径问题，不能将它们统称为“服务暂不可用”。参考：[接口文档](https://api-docs.deepseek.com/api/create-chat-completion/)、[错误码](https://api-docs.deepseek.com/quick_start/error_codes/)、[V4 发布说明](https://api-docs.deepseek.com/news/news260424/)。

2026-09-27 网络排查：一加 13 可通过 HTTPS 连接 `api.deepseek.com/chat/completions`，未认证的探测请求迅速返回 401，说明手机至服务端的 DNS、TCP、TLS 路径可用。此前客户端读取超时仅 8 秒，无法据此认定服务不可达。0.2.2-alpha 将连接 / 单次读取 / 总等待分别调整为 10 / 30 / 45 秒，单独提示连接和响应超时。DeepSeek 专用请求设置 `thinking.type=disabled`、`response_format.type=json_object`、`max_tokens=700`；用户可一键切换 `deepseek-flash`，保留原 Key。依据：[模型列表与价格](https://api-docs.deepseek.com/quick_start/pricing/)、[思考模式](https://api-docs.deepseek.com/guides/thinking_mode/)、[JSON 输出](https://api-docs.deepseek.com/guides/json_mode/)。真实授权 POST 尚需用户在手机内用虚构消息重试，不能把连通性探测当作生成成功。

待用户选择服务商后记录：精确模型 ID、接口域名及路径、数据处理条款、计费页面、请求兼容性、首试成功率、三条差异、时延。先用至少 30 条虚构中文场景做初选，正式验收再用计划中的 120 条固定评测集。API Key 只输入手机应用，不写入此文档或聊天。
