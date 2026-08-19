let assistantController = null
const assistantEventHandlers = new Set()

export function registerAiAssistantController(controller) {
  assistantController = controller || null
  return () => {
    if (assistantController === controller) {
      assistantController = null
    }
  }
}

export async function openSystemAiAssistant(options = {}) {
  if (!assistantController?.openAssistant) {
    // 详细日志：controller 未注册，说明 AiChatWorkspace 组件未挂载或未调用 registerAiAssistantController
    console.error(
      '[openSystemAiAssistant] 助手控制器未注册。请确认 AiChatWorkspace 组件已挂载且调用了 registerAiAssistantController。',
      { hasController: !!assistantController, optionsKeys: Object.keys(options || {}) }
    )
    return false
  }
  try {
    await assistantController.openAssistant(options)
    return true
  } catch (error) {
    // 详细日志：openAssistant 内部抛出异常
    console.error('[openSystemAiAssistant] openAssistant 执行异常:', error)
    throw error
  }
}

export async function closeSystemAiAssistant() {
  if (!assistantController?.closeAssistant) {
    return false
  }
  assistantController.closeAssistant()
  return true
}

export async function syncSystemAiAssistantPayload(payload, reason = 'manual') {
  if (!assistantController?.syncPagePayloadToWorkspace) {
    return null
  }
  return assistantController.syncPagePayloadToWorkspace(payload, reason)
}

// 向聊天流追加一条携带自定义卡片的助手消息（如"表格预览 + 确认填入"）。
// 业务层在 onTaskEnded / onToolCall 等回调里调用，替代居中弹出的确认框。
export function appendSystemAiAssistantCard(card) {
  if (!assistantController?.appendAssistantCard) {
    console.error('[appendSystemAiAssistantCard] 助手控制器未注册或缺少 appendAssistantCard')
    return null
  }
  return assistantController.appendAssistantCard(card)
}

export function emitSystemAiAssistantEvent(eventName, payload) {
  assistantEventHandlers.forEach((handler) => {
    try {
      handler(eventName, payload)
    } catch (error) {
      // 事件桥接只做页面联动，不阻断 AI 主流程。
    }
  })
}

export function onSystemAiAssistantEvent(handler) {
  if (typeof handler !== 'function') {
    return () => {}
  }
  assistantEventHandlers.add(handler)
  return () => {
    assistantEventHandlers.delete(handler)
  }
}

// 挂到 window 上，供非 Vue 场景或第三方脚本直接调用。
// 使用 __CODEY_AI_ASSISTANT__ 命名空间避免与页面其他全局变量冲突。
if (typeof window !== 'undefined') {
  const bridge = {
    registerAiAssistantController,
    openSystemAiAssistant,
    closeSystemAiAssistant,
    syncSystemAiAssistantPayload,
    appendSystemAiAssistantCard,
    emitSystemAiAssistantEvent,
    onSystemAiAssistantEvent,
  }
  window.__CODEY_AI_ASSISTANT__ = bridge
  // 保持独立挂载以兼容旧版调用方
  Object.keys(bridge).forEach((key) => {
    if (!window[key]) {
      window[key] = bridge[key]
    }
  })
}
