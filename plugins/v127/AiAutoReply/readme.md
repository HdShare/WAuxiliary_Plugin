# 🤖 AI智能回复插件

一个功能完善的微信 AI 自动回复插件，基于 WAuxiliary 框架。

## ✨ 功能特性

- **多API支持**：OpenAI / Claude / DeepSeek / 任何 OpenAI 兼容接口
- **多轮对话记忆**：保留最近N轮对话上下文，AI 记住之前聊了什么
- **多种触发模式**：
  - `at_me` - 仅@机器人时回复
  - `all` - 回复所有消息
  - `keyword` - 包含指定关键词时回复
  - `prefix` - 以指定前缀开头的消息触发
- **黑白名单**：支持用户级和群聊级过滤
- **频率限制**：防止刷屏，每分钟最大回复数可配
- **在线配置**：通过聊天命令实时修改配置
- **群聊支持**：可选是否在群聊中工作，@回复自动定位发送者

## 📦 安装

1. 将整个 `AiAutoReply` 文件夹放入 WAuxiliary 的 plugins 目录
2. 在 WAuxiliary 中启用插件
3. 配置 API Key（必填）

## ⚙️ 配置说明

首次运行后会在插件目录生成 `config.prop`，可手动编辑或通过命令在线修改。

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `provider` | `openai` | API提供方：openai/claude/deepseek/custom |
| `api_key` | (空) | **必填** API密钥 |
| `api_url` | (默认) | 自定义API地址 |
| `model` | `gpt-3.5-turbo` | 模型名称 |
| `system_prompt` | 友好AI助手 | 系统提示词，定义AI人设 |
| `max_tokens` | `1000` | 最大回复长度 |
| `temperature` | `0.7` | 创意度 0-2 |
| `trigger_mode` | `at_me` | 触发模式 |
| `trigger_keyword` | (空) | 关键词触发时的关键词 |
| `trigger_prefix` | (空) | 前缀触发时的前缀 |
| `enable_group` | `true` | 是否在群聊中工作 |
| `blacklist_users` | (空) | 用户黑名单，逗号分隔wxid |
| `whitelist_users` | (空) | 用户白名单（空=不启用） |
| `blacklist_groups` | (空) | 群聊黑名单 |
| `whitelist_groups` | (空) | 群聊白名单 |
| `max_history` | `10` | 保留最近N轮对话 |
| `rate_limit` | `10` | 每分钟最大回复数 |
| `reply_prefix` | (空) | 回复前缀，如 `[AI] ` |
| `typing_hint` | `true` | 群聊中发送"正在思考"提示 |
| `timeout` | `30` | API超时秒数 |
| `enabled` | `true` | 插件开关 |

## 🎮 聊天命令

| 命令 | 说明 |
|------|------|
| `/aihelp` | 显示帮助信息 |
| `/aistatus` | 查看插件运行状态 |
| `/aiconfig` | 查看当前所有配置 |
| `/aiset key=value` | 在线修改配置项 |
| `/aiclear` | 清除当前对话记忆 |
| `/airestart` | 重启插件 |

## 🔌 各平台配置示例

### OpenAI
```
provider=openai
api_key=sk-xxxxxxxxxxxxxxxx
model=gpt-4o-mini
api_url=(留空使用默认)
```

### DeepSeek
```
provider=deepseek
api_key=sk-xxxxxxxxxxxxxxxx
model=deepseek-chat
api_url=(留空使用默认)
```

### Claude (Anthropic)
```
provider=claude
api_key=sk-ant-xxxxxxxxxxxxxxxx
model=claude-3-5-sonnet-20241022
```

### 自定义 (如 OneAPI / New API / 中转)
```
provider=custom
api_key=sk-xxxxxxxxxxxxxxxx
api_url=https://your-proxy.com/v1/chat/completions
model=gpt-4
```

## 📝 提示词示例

**通用助手：**
```
你是一个友好的AI助手，用简洁自然的中文回复。不要透露你是AI。
```

**角色扮演：**
```
你现在是一只名叫"小咪"的猫娘。你会用可爱的语气说话，句末加"喵~"。
你非常黏人，喜欢被摸头。你的主人叫你的时候你会很开心。
```

**专业客服：**
```
你是XX公司的专业客服代表。语气礼貌专业，遇到不懂的问题请用户留下联系方式。
```

## ⚠️ 注意事项

- API Key 是必填项，没有配置则插件不会工作
- 群聊中建议使用 `at_me` 触发模式，避免AI乱插话
- 适当设置 `rate_limit` 防止被API限流
- `system_prompt` 中不要包含敏感信息

---

FishTotal © 2026
