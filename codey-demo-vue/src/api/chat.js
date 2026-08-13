import { fetchJson } from './http'

// 统一封装聊天接口，便于其它页面直接复用这套组件能力。
export function createChatApi(baseUrl = '/api/chat', historyBaseUrl = '/api/chat/history') {
  return {
    openSession(payload = {}) {
      return fetchJson(`${baseUrl}/sessions`, {
        method: 'POST',
        body: JSON.stringify(payload),
      })
    },

    resumeSession(sessionId, payload = {}) {
      return fetchJson(`${baseUrl}/sessions/${encodeURIComponent(sessionId)}/resume`, {
        method: 'POST',
        body: JSON.stringify(payload),
      })
    },

    sendMessage(sessionId, payload = {}) {
      return fetchJson(`${baseUrl}/sessions/${encodeURIComponent(sessionId)}/messages`, {
        method: 'POST',
        body: JSON.stringify(payload),
      })
    },

    closeSession(sessionId) {
      return fetchJson(`${baseUrl}/sessions/${encodeURIComponent(sessionId)}/close`, {
        method: 'POST',
      })
    },

    confirmDecision(sessionId, confirmationId, payload = {}) {
      return fetchJson(`${baseUrl}/sessions/${encodeURIComponent(sessionId)}/confirmations/${encodeURIComponent(confirmationId)}`, {
        method: 'POST',
        body: JSON.stringify(payload),
      })
    },

    listSessions() {
      return fetchJson(`${historyBaseUrl}/sessions`)
    },

    getSessionDetail(sessionId) {
      return fetchJson(`${historyBaseUrl}/sessions/${encodeURIComponent(sessionId)}`)
    },

    deleteHistorySession(sessionId) {
      return fetchJson(`${historyBaseUrl}/sessions/${encodeURIComponent(sessionId)}/delete`, {
        method: 'POST',
      })
    },

    clearHistorySessions() {
      return fetchJson(`${historyBaseUrl}/sessions/clear`, {
        method: 'POST',
      })
    },

    createEventSource(sessionId) {
      return new EventSource(`${baseUrl}/sessions/${encodeURIComponent(sessionId)}/events`)
    },
  }
}
