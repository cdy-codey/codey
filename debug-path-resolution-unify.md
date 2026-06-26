# Debug Path Resolution Unify [OPEN]

## 用户要求
- 文件路径以前端入参为准。
- 后端只负责把这个前端相对路径挂到工作目录下。
- 大模型、工具调用、日志回显都必须统一同一种相对路径语义。

## 当前症状
- 仍然出现 `business-procurement/business-procurement/business-procurement/context.json`。
- 说明至少有一段链路没有把“前端入参”当作最终相对路径，而是又叠加了工作目录。

## 可证伪假设
1. `ToolCallProcessor` 已经把工具参数规范成前端相对路径，但具体工具执行前又二次拼接了 `workingDirectory`。
2. `session.workingDirectory` 与工具上下文 `workspaceRoot` 的语义不一致，一个表示工作根，一个表示项目子目录，导致重复挂载。
3. `read_file`、`write_file`、`edit_file`、`delete_file`、`list_workspace` 对路径字段的标准化入口不一致，部分工具仍绕过统一归一化。
4. 模型提示词里暴露给大模型的“当前工作目录”和“当前文件路径”仍然混用了工作根路径与前端文件路径，导致模型持续生成重复前缀。
5. 会话恢复或历史归档链路把旧格式路径重新注入了新一轮会话，使得修复后的执行链路又收到脏路径。

## 证据
- 历史 `model_input.json` 已多次出现读写路径不一致。
- 用户进一步明确规则：“文件应该以前端入参为准，然后挂到工作目录下。”
- 前端会话入参已确认：`workingDirectory=./business-procurement`，`contextFiles=[context.json]`。
- 前端创建工作文件入参已确认：`path=business-procurement/context.json`，说明工作区文件创建和 AI 会话文件定位是两层语义。
- 最新日志显示 system prompt 的“补充文件”被不断灌入工具运行中生成的路径，形成多层 `business-procurement/.../context.json` 污染链。

## 下一步
- 检查后端请求入参、提示词组装、工具参数标准化、具体工具执行四段链路。
- 找到第一处偏离“以前端入参为准”的位置后再修改。

## 已确认根因
1. 前端原始上下文与工具运行痕迹混存到同一份 `contextFiles/contextNotes`。
2. `PromptAssembler` 读取了被污染后的稳定上下文，导致模型下一轮继续学习错误路径。
3. 工具执行成功后，`ToolCallProcessor` 又把运行时路径反灌进稳定上下文，形成滚雪球污染。

## 本轮修复
- 前端传入的 `contextFiles/contextNotes` 改为写入 `userContextFiles/userContextNotes`。
- system prompt 只读取 `userContext*`，不再读取工具污染后的稳定上下文。
- 工具执行成功后不再把读写文件路径追加回 `contextFiles`，只保留 `lastEditedFilePath` 和读文件范围记忆。
