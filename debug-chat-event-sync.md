# Debug Session: chat-event-sync [OPEN]

## 背景
- 问题描述：前后端联调仍然失败，怀疑事件处理链路存在问题。
- 当前目标：确认前端 SSE/事件消费、后端事件发布、消息发送接口三者之间的实际运行差异。

## 待验证假设
- 假设 1：后端 SSE 事件名与前端 `addEventListener` 监听名称不一致，导致事件到达但未被消费。
- 假设 2：后端事件数据结构与前端 `readEventPayload()` / `appendAssistantText()` 的字段读取路径不一致，导致文本增量未正确拼接。
- 假设 3：消息发送接口在 SSE 建连前或建连过程中异常结束，前端把接口错误误判成事件处理异常。
- 假设 4：前端在 `final_summary`、`error` 或 `security_event` 分支里过早重置状态，导致后续流式事件被覆盖或丢失。
- 假设 5：后端实际推送了事件，但因为鉴权或模型调用失败，事件流只包含错误/结束事件而不包含文本事件。

## 证据收集计划
- 检查后端事件类型定义与 SSE 推送实现。
- 检查前端事件订阅、状态变更、消息拼接逻辑。
- 启动前后端后抓取一次真实会话的接口响应、SSE 流和页面表现。
- 根据证据判断是事件命名、载荷结构、状态管理还是后端运行失败。

## 当前状态
- 已完成真实链路复现，尚未修改业务逻辑。
- 关键证据：
- `tmp-sse.log` 证明 SSE 至少收到了 `connected` 和两个 `debug_trace` 事件。
- `tmp-sse.log` 未出现 `model_text_delta`、`security_event`、`final_summary` 等终态事件。
- 后端运行日志出现 `Model HTTP error 401: Authentication Fails (governor)`，说明模型调用在网关层直接失败。
- `LoopTurnPreparer` 会把完整 `debugView` 通过 `debug_trace(message_view_built)` 发给 SSE，内容非常长。
- `LoopTurnEngine` / `ModelTurnExecutor` 在模型网关抛异常时没有兜底发布错误事件，导致前端只能看到 REST 500，看不到 SSE 终态说明。

## 当前判断
- 假设 1：部分成立。事件名并非主因，但前端确实没有覆盖 `model_output`、`model_thinking_delta`、`model_tool_call_started`。
- 假设 2：当前未见直接证据，因根本没有收到文本增量事件。
- 假设 3：成立。`/messages` 在模型调用阶段返回 500，前端只能走 fetch 异常分支。
- 假设 4：暂未证实。当前更早失败于后端未推送终态事件。
- 假设 5：成立。后端仅推送了调试事件，未推送文本或错误终态事件。

## 下一步
- 先补最小化前端观测，记录未覆盖事件类型和事件 payload 摘要。
- 再修复后端异常路径，确保模型调用失败时也会发布可消费的错误终态事件。
