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
    return false
  }
  await assistantController.openAssistant(options)
  return true
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
