import { createChatApi } from '../api/chat'
import { createWorkspaceApi } from '../api/workspace'

/**
 * 统一封装 AiChatWorkspace 组件所需的所有业务处理函数，
 * 避免每个使用 AiChatWorkspace 的页面重复编写相同的 handler 代码。
 *
 * @param {object} [options]
 * @param {object} [options.chatApi]   可选，外部传入的 chatApi 实例（不传则自动创建）
 * @param {object} [options.workspaceApi] 可选，外部传入的 workspaceApi 实例（不传则自动创建）
 * @returns {{
 *   // 单个 handler，方便组件按需绑定（如 :open-session="openSession"）
 *   openSession,
 *   resumeSession,
 *   sendMessage,
 *   closeSession,
 *   listSessions,
 *   getSessionDetail,
 *   deleteHistorySession,
 *   clearHistorySessions,
 *   createEventSource,
 *   queryWorkspace,
 *   writeWorkspaceFile,
 *   // 统一对象，方便通过 v-bind="aiAssistantHandlers" 一次性绑定
 *   aiAssistantHandlers,
 *   // 原始 API 实例，供组件做额外操作（如 push / remove）
 *   chatApi,
 *   workspaceApi,
 * }}
 */
export function useAiAssistantHandlers(options = {}) {
  // 聊天相关 API
  const chatApi = options.chatApi || createChatApi()
  // 工作目录相关 API
  const workspaceApi = options.workspaceApi || createWorkspaceApi()

  function openSession(payload) {
    return chatApi.openSession(payload)
  }

  function resumeSession(sessionId, payload) {
    return chatApi.resumeSession(sessionId, payload)
  }

  function sendMessage(sessionId, payload) {
    return chatApi.sendMessage(sessionId, payload)
  }

  function closeSession(sessionId) {
    return chatApi.closeSession(sessionId)
  }

  function listSessions() {
    return chatApi.listSessions()
  }

  function getSessionDetail(sessionId) {
    return chatApi.getSessionDetail(sessionId)
  }

  function deleteHistorySession(sessionId) {
    return chatApi.deleteHistorySession(sessionId)
  }

  function clearHistorySessions() {
    return chatApi.clearHistorySessions()
  }

  function createEventSource(sessionId) {
    return chatApi.createEventSource(sessionId)
  }

  function confirmDecision(sessionId, confirmationId, payload) {
    return chatApi.confirmDecision(sessionId, confirmationId, payload)
  }

  function queryWorkspace(params = {}) {
    const path = typeof params === 'string' ? params : (params?.path || '')
    const readMode = typeof params === 'object' ? params?.readMode : ''
    // 结束阶段允许按 readMode 切换读取语义：JSON 页面读 query-json，源码页读工作区快照。
    if (readMode === 'snapshot') {
      return workspaceApi.query(path)
    }
    return workspaceApi.queryJson(path)
  }

  function writeWorkspaceFile(params = {}) {
    const path = typeof params === 'string' ? params : (params?.path || '')
    const content = typeof params === 'string' ? '' : (params?.content ?? '')
    return workspaceApi.updateFileContent(path, content)
  }

  // 统一对象，便于 v-bind="aiAssistantHandlers" 一次性绑定
  const aiAssistantHandlers = {
    openSession,
    resumeSession,
    sendMessage,
    closeSession,
    listSessions,
    getSessionDetail,
    deleteHistorySession,
    clearHistorySessions,
    createEventSource,
    confirmDecision,
    queryWorkspace,
    writeWorkspaceFile,
  }

  return {
    // 单个 handler（兼容 :open-session="openSession" 方式）
    openSession,
    resumeSession,
    sendMessage,
    closeSession,
    listSessions,
    getSessionDetail,
    deleteHistorySession,
    clearHistorySessions,
    createEventSource,
    confirmDecision,
    queryWorkspace,
    writeWorkspaceFile,
    // 统一 v-bind 对象（兼容 v-bind="aiAssistantHandlers" 方式）
    aiAssistantHandlers,
    // 原始 API 实例，供需要额外操作（如 push / remove）的组件使用
    chatApi,
    workspaceApi,
  }
}
