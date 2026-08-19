// 统一入口：组件、composable、工具函数全部从 index 导出，业务方按需引入。

// 主组件
export { default as AiChatWorkspace } from './AiChatWorkspace.vue'

// composable
export { useAiChat } from './useAiChat.js'

// 消息展示工具
export { getMessageBlocks, formatTime } from './chatPresentation.js'

// 消息适配 / 解析工具
export {
  normalizeContent,
  parseModelOutputPayload,
  extractAssistantContentFromFinalResult,
  extractFinalSummaryText,
  normalizeFinalAssistantContent,
  buildToolResultPreview,
  normalizeToolMessageContent,
} from './chatMessageAdapter.js'

// 跨上下文桥接（页面 ↔ 聊天组件事件联动）
export {
  registerAiAssistantController,
  openSystemAiAssistant,
  closeSystemAiAssistant,
  syncSystemAiAssistantPayload,
  appendSystemAiAssistantCard,
  emitSystemAiAssistantEvent,
  onSystemAiAssistantEvent,
} from './assistantBridge.js'
