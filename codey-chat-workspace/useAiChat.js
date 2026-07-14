import { computed, onBeforeUnmount, ref } from 'vue'
import {
  buildToolResultPreview,
  extractFinalSummaryText,
  normalizeContent,
  normalizeFinalAssistantContent,
  normalizeToolMessageContent,
  parseModelOutputPayload,
} from './chatMessageAdapter'

const STREAM_EVENT_TYPES = [
  'connected',
  'model_text_delta',
  'model_thinking_delta',
  'model_tool_call_started',
  'tool_execution_started',
  'tool_call',
  'verification',
  //'human_decision',
  'task_status',
  'final_summary',
  'model_output',
  'debug_trace',
  'security_event',
]

function createChatApiAdapter(options = {}) {
  const requiredFunctionNames = [
    'openSession',
    'resumeSession',
    'sendMessage',
    'closeSession',
    'listSessions',
    'getSessionDetail',
    'deleteHistorySession',
    'clearHistorySessions',
    'createEventSource',
  ]
  const missingFunctions = requiredFunctionNames.filter((name) => typeof options?.[name] !== 'function')
  if (missingFunctions.length > 0) {
    throw new Error(`[useAiChat] 缺少请求函数: ${missingFunctions.join(', ')}`)
  }
  return {
    openSession: options.openSession,
    resumeSession: options.resumeSession,
    sendMessage: options.sendMessage,
    closeSession: options.closeSession,
    listSessions: options.listSessions,
    getSessionDetail: options.getSessionDetail,
    deleteHistorySession: options.deleteHistorySession,
    clearHistorySessions: options.clearHistorySessions,
    createEventSource: options.createEventSource,
  }
}

/**
 * 抽离会话状态与流式事件处理，便于任意页面复用同一套聊天能力。
 */
export function useAiChat(options = {}) {
  const chatApi = createChatApiAdapter(options)
  const historyEnabled = resolveBooleanOption('historyEnabled', true)
  const workingDirectory = ref(options.defaultWorkingDirectory || '')
  const inputValue = ref('')
  const sessionId = ref('')
  const selectedSessionId = ref('')
  const isLiveSession = ref(false)
  const isLoadingSessions = ref(false)
  const isLoadingDetail = ref(false)
  const isSending = ref(false)
  const errorMessage = ref('')
  const connectionStatus = ref('未连接')
  const archivedSessions = ref([])
  const messageSeed = ref(0)
  const messages = ref(createWelcomeMessages())
  const eventSource = ref(null)
  const activeAssistantId = ref('')
  const resumeContext = ref(null)

  function invokeHook(name, payload) {
    if (typeof options?.[name] !== 'function') {
      return
    }
    try {
      options[name](payload)
    } catch (error) {
      // 钩子只影响业务联动，不阻断聊天主链路。
    }
  }

  async function invokeAsyncHook(name, payload) {
    if (typeof options?.[name] !== 'function') {
      return undefined
    }
    return await options[name](payload)
  }

  function normalizeStringArray(values) {
    if (!Array.isArray(values)) {
      return []
    }
    return values
      .map((item) => (typeof item === 'string' ? item.trim() : ''))
      .filter((item) => item.length > 0)
  }

  function resolveBooleanOption(name, defaultValue) {
    const rawValue = typeof options?.[name] === 'function' ? options[name]() : options?.[name]
    return typeof rawValue === 'boolean' ? rawValue : defaultValue
  }

  function resolveStringOption(name, defaultValue = '') {
    const rawValue = typeof options?.[name] === 'function' ? options[name]() : options?.[name]
    return typeof rawValue === 'string' ? rawValue.trim() : defaultValue
  }

  function resolveIncludeThinking(explicitValue) {
    if (typeof explicitValue === 'boolean') {
      return explicitValue
    }
    // 是否返回思考内容由页面显式控制，默认保持兼容开启。
    return resolveBooleanOption('includeThinking', true)
  }

  function includeArchivedSession(entry) {
    if (typeof options?.filterArchivedSession !== 'function') {
      return true
    }
    try {
      return options.filterArchivedSession(entry) !== false
    } catch (error) {
      return true
    }
  }

  // 每次发起会话或发送消息时，都从外部工作区重新拉取一次上下文，
  // 避免用户切换文件后模型仍然沿用旧的文件路径。
  function buildRequestPayload(extra = {}) {
    const context = typeof options?.buildRequestContext === 'function' ? options.buildRequestContext() || {} : {}
    const skillName = resolveStringOption('skillName', '')
    return {
      skillName,
      workingDirectory: workingDirectory.value,
      includeThinking: resolveIncludeThinking(extra.includeThinking),
      ...context,
      ...extra,
      contextFiles: normalizeStringArray(extra.contextFiles ?? context.contextFiles),
      contextNotes: normalizeStringArray(extra.contextNotes ?? context.contextNotes),
      chatHistory: normalizeStringArray(extra.chatHistory ?? context.chatHistory),
      identities: normalizeStringArray(extra.identities ?? context.identities ?? options.identities),
    }
  }

  const canSend = computed(() => inputValue.value.trim().length > 0 && !isSending.value)

  function createWelcomeMessages() {
    const suggestions = resolveWelcomeSuggestions()
    return [
      {
        id: nextMessageId('assistant'),
        role: 'assistant',
        content: resolveStringOption('welcomeMessage', '直接输入目标即可开始，会话会按 console 的方式持续保持。'),
        reasoning: '',
        name: '',
        toolCalls: [],
        live: false,
        suggestions: suggestions.length > 0 ? suggestions : undefined,
      },
    ]
  }

  function resolveWelcomeSuggestions() {
    const rawValue = typeof options?.welcomeSuggestions === 'function'
      ? options.welcomeSuggestions()
      : options?.welcomeSuggestions
    if (!Array.isArray(rawValue)) {
      return []
    }
    return rawValue.filter((item) => typeof item === 'string' && item.trim().length > 0)
  }

  function nextMessageId(prefix) {
    messageSeed.value += 1
    return `${prefix}-${Date.now()}-${messageSeed.value}`
  }

  function createMessage(role, content = '', extra = {}) {
    return {
      id: nextMessageId(role),
      role,
      content,
      reasoning: extra.reasoning || '',
      name: extra.name || '',
      toolCalls: extra.toolCalls || [],
      live: !!extra.live,
    }
  }

  function resetComposerState() {
    activeAssistantId.value = ''
    isSending.value = false
  }
  function isEmptyAssistantMessage(message) {
    if (!message || message.role !== 'assistant') {
      return false
    }
    return !normalizeContent(message.content).trim()
  }
  function closeActiveAssistantMessage() {
    const currentId = activeAssistantId.value
    if (!currentId) {
      return
    }
    const messageIndex = messages.value.findIndex((item) => item.id === currentId)
    if (messageIndex >= 0) {
      const existing = messages.value[messageIndex]
      existing.live = false
      // 只有思考过程、没有最终正文的临时消息在结束后直接移除，避免界面残留空白回复。
      if (isEmptyAssistantMessage(existing)) {
        messages.value.splice(messageIndex, 1)
      }
    }
    activeAssistantId.value = ''
  }
  function disconnectEventSource() {
    if (eventSource.value) {
      eventSource.value.close()
      eventSource.value = null
    }
  }

  function resetSessionView() {
    resetComposerState()
    sessionId.value = ''
    selectedSessionId.value = ''
    isLiveSession.value = false
    connectionStatus.value = '未连接'
    errorMessage.value = ''
    resumeContext.value = null
    messages.value = createWelcomeMessages()
  }

  function ensureActiveAssistantMessage() {
    const currentId = activeAssistantId.value
    if (currentId) {
      const existing = messages.value.find((item) => item.id === currentId)
      if (existing) {
        return existing
      }
    }
    const message = createMessage('assistant', '', { live: true })
    messages.value.push(message)
    activeAssistantId.value = message.id
    return message
  }

  function mergeToolCalls(message, toolCalls = []) {
    if (!message || !Array.isArray(toolCalls) || !toolCalls.length) {
      return
    }
    const merged = new Set([...(message.toolCalls || []), ...toolCalls.filter(Boolean)])
    message.toolCalls = Array.from(merged)
  }

  function syncLiveSessionSummary(firstPrompt) {
    const preview = firstPrompt || '新建会话'
    const existing = archivedSessions.value.find((item) => item.sessionId === sessionId.value)
    if (existing) {
      existing.updatedAt = new Date().toISOString()
      existing.title = existing.title || preview
      existing.preview = existing.preview || preview
      return
    }
    archivedSessions.value.unshift({
      sessionId: sessionId.value,
      title: preview.slice(0, 20),
      preview: preview.slice(0, 60),
      updatedAt: new Date().toISOString(),
      requestLogCount: 0,
      messageCount: 0,
      hasEventLog: false,
    })
  }

  function applyArchiveDetail(detail) {
    resumeContext.value = detail?.resumeContext || null
    const archiveMessages = Array.isArray(detail?.messages) ? detail.messages : []
    messages.value = archiveMessages.length
      ? archiveMessages.map((item) =>
          createMessage(item.role, normalizeContent(item.content), {
            reasoning: normalizeContent(item.reasoning),
            name: normalizeContent(item.displayName || item.name),
            toolCalls: Array.isArray(item.toolCalls) ? item.toolCalls.filter(Boolean) : [],
            live: false,
          }),
        )
      : createWelcomeMessages()
    messages.value = messages.value.map((item) =>
      item.role === 'tool'
        ? {
            ...item,
            content: normalizeToolMessageContent(item.name, item.content),
          }
        : item,
    )
  }

  async function loadSessions(preferredSessionId = '') {
    if (!historyEnabled) {
      archivedSessions.value = []
      if (preferredSessionId) {
        selectedSessionId.value = preferredSessionId
      }
      return []
    }
    isLoadingSessions.value = true
    try {
      const data = await chatApi.listSessions()
      archivedSessions.value = Array.isArray(data) ? data.filter((item) => includeArchivedSession(item)) : []
      if (preferredSessionId) {
        selectedSessionId.value = preferredSessionId
      }
    } catch (error) {
      errorMessage.value = error.message || '读取会话列表失败'
    } finally {
      isLoadingSessions.value = false
    }
  }

  async function selectSession(targetSessionId) {
    if (!historyEnabled || !targetSessionId) {
      return
    }
    selectedSessionId.value = targetSessionId
    isLoadingDetail.value = true
    errorMessage.value = ''
    try {
      const detail = await chatApi.getSessionDetail(targetSessionId)
      applyArchiveDetail(detail)
      disconnectEventSource()
      if (sessionId.value !== targetSessionId) {
        isLiveSession.value = false
        connectionStatus.value = '历史会话'
      }
    } catch (error) {
      errorMessage.value = error.message || '读取会话详情失败'
    } finally {
      isLoadingDetail.value = false
    }
  }
function parseEventPayload(event) {
    try {
      return event?.data ? JSON.parse(event.data) : null
    } catch (error) {
      return null
    }
  }

  function handleConnected(payload) {
    connectionStatus.value = `实时会话：${payload?.sessionId || sessionId.value}`
  }

  function handleModelTextDelta(payload) {
    const message = ensureActiveAssistantMessage()
    message.content += payload?.message || payload?.payload?.delta || ''
  }

  function handleModelThinkingDelta(payload) {
    const message = ensureActiveAssistantMessage()
    message.reasoning += payload?.message || payload?.payload?.delta || ''
  }

  function handleModelToolCallStarted(payload) {
    const message = ensureActiveAssistantMessage()
    mergeToolCalls(message, [normalizeContent(payload?.payload?.displayName || payload?.message)])
  }

  function handleTaskStatus(payload) {
    const status = payload?.payload?.status || payload?.message || ''
    const terminal = payload?.payload?.terminal === true
    const success = payload?.payload?.success === true
    if (status === 'accepted') {
      invokeHook('onTaskAccepted', { sessionId: sessionId.value, payload })
      return
    }
    if (terminal && success) {
      invokeHook('onTaskCompleted', { sessionId: sessionId.value, payload })
      return
    }
    if (terminal) {
      const message = payload?.payload?.message || payload?.message || '任务执行失败'
      errorMessage.value = message
      closeActiveAssistantMessage()
      isSending.value = false
      invokeHook('onTaskFailed', { sessionId: sessionId.value, message, payload })
    }
  }

  function handleFinalSummary(payload) {
    const message = ensureActiveAssistantMessage()
    // 最终摘要可能是纯文本，也可能是 JSON 字符串或 markdown code fence；这里统一提取可展示的 summary。
    const summary = extractFinalSummaryText(payload)
    // 流式阶段可能已把最终 FINISH JSON 塞进正文；这里统一收敛成给用户展示的自然语言摘要。
    // AI修改：结束语提取成功后，优先把正文稳定成可展示文本，避免业务侧继续拿到伪 JSON - 2026-06-29
    message.content = normalizeFinalAssistantContent(message.content, summary)
    const finalSummary = summary || message.content
    // 对外暴露最终回答文本，便于业务页面按约定解析 JSON 并自动回填表单。
    invokeHook('onAssistantFinished', {
      sessionId: sessionId.value,
      summary: finalSummary,
      content: message.content,
      reasoning: message.reasoning,
      payload,
    })
    closeActiveAssistantMessage()
    isSending.value = false
    invokeHook('onFinalSummary', { sessionId: sessionId.value, summary: finalSummary, payload })
    loadSessions(sessionId.value)
  }

  function handleToolCall(payload) {
    const toolName =
      payload?.payload?.request?.toolName ||
      payload?.payload?.request?.tool_name ||
      payload?.payload?.name ||
      ''
    closeActiveAssistantMessage()
    messages.value.push(
      createMessage('tool', buildToolResultPreview(payload), {
        name: normalizeContent(payload?.payload?.displayName || payload?.payload?.request?.displayName),
        live: false,
      }),
    )
    invokeHook('onToolCall', { sessionId: sessionId.value, toolName, payload })
  }

  function handleVerificationOrHumanDecision(payload, eventType) {
    closeActiveAssistantMessage()
    messages.value.push(
      createMessage('tool', payload?.message || payload?.stage || eventType, {
        name: eventType,
        live: false,
      }),
    )
  }

  function handleSecurityEvent(payload) {
    errorMessage.value = payload?.message || '检测到安全事件'
  }

  function handleModelOutput(payload) {
    const parsedOutput = parseModelOutputPayload(payload)
    if (!parsedOutput) {
      return
    }
    const message = ensureActiveAssistantMessage()
    if (parsedOutput.reasoning && parsedOutput.reasoning.length >= message.reasoning.length) {
      message.reasoning = parsedOutput.reasoning
    }
    if (parsedOutput.content && parsedOutput.content.length >= message.content.length) {
      message.content = parsedOutput.content
    }
    mergeToolCalls(message, parsedOutput.toolCalls)
    if (parsedOutput.finishReason === 'tool_calls') {
      closeActiveAssistantMessage()
    }
  }

  // 流式事件处理器映射表，按事件类型分派到对应的处理函数
  const STREAM_EVENT_HANDLERS = {
    connected: handleConnected,
    model_text_delta: handleModelTextDelta,
    model_thinking_delta: handleModelThinkingDelta,
    model_tool_call_started: handleModelToolCallStarted,
    task_status: handleTaskStatus,
    final_summary: handleFinalSummary,
    tool_call: handleToolCall,
    security_event: handleSecurityEvent,
    model_output: handleModelOutput,
    // 以下事件类型只需记录或无需处理
    tool_execution_started: () => {},
    debug_trace: () => {},
    verification: (payload, eventType) => handleVerificationOrHumanDecision(payload, eventType),
   //human_decision: (payload, eventType) => handleVerificationOrHumanDecision(payload, eventType),
  }

  function handleStreamEvent(eventType, event) {
    const payload = parseEventPayload(event)
    const handler = STREAM_EVENT_HANDLERS[eventType]
    if (handler) {
      handler(payload, eventType)
    }
  }
  function connectEventStream(targetSessionId) {
    disconnectEventSource()
    const source = chatApi.createEventSource(targetSessionId)
    eventSource.value = source

    source.onopen = () => {
      connectionStatus.value = 'SSE 已连接'
    }

    source.onerror = () => {
      connectionStatus.value = '连接已中断'
    }

    STREAM_EVENT_TYPES.forEach((eventType) => {
      source.addEventListener(eventType, (event) => {
        handleStreamEvent(eventType, event)
      })
    })
  }

  async function restoreArchivedSessionIfNeeded() {
    if (!selectedSessionId.value || isLiveSession.value) {
      return sessionId.value
    }
    const restoredSessionId = resumeContext.value?.sessionId || selectedSessionId.value
    const restoredHistory = Array.isArray(resumeContext.value?.chatHistory) ? resumeContext.value.chatHistory : []
    const session = await chatApi.resumeSession(
      restoredSessionId,
      buildRequestPayload({
        chatHistory: restoredHistory,
      }),
    )
    sessionId.value = session?.sessionId || restoredSessionId
    selectedSessionId.value = sessionId.value
    isLiveSession.value = true
    connectionStatus.value = '正在恢复历史会话...'
    connectEventStream(sessionId.value)
    return sessionId.value
  }

  async function ensureLiveSession(firstPrompt) {
    if (sessionId.value && isLiveSession.value) {
      return sessionId.value
    }

    if (selectedSessionId.value && resumeContext.value?.sessionId) {
      return restoreArchivedSessionIfNeeded()
    }

    const session = await chatApi.openSession(buildRequestPayload())
    sessionId.value = session?.sessionId || ''
    selectedSessionId.value = sessionId.value
    isLiveSession.value = true
    connectionStatus.value = '正在建立实时连接...'
    syncLiveSessionSummary(firstPrompt)
    connectEventStream(sessionId.value)
    return sessionId.value
  }

  async function sendPrompt(prompt = inputValue.value) {
    const goal = normalizeContent(prompt).trim()
    if (!goal || isSending.value) {
      return
    }

    try {
      errorMessage.value = ''
      isSending.value = true
      const liveSessionId = await ensureLiveSession(goal)

      const promptAfterHook = await invokeAsyncHook('onBeforeSend', {
        prompt: goal,
      })
      const finalPrompt = typeof promptAfterHook === 'string' && promptAfterHook.trim()
        ? promptAfterHook.trim()
        : goal

      messages.value.push(createMessage('user', finalPrompt))
      syncLiveSessionSummary(finalPrompt)
      inputValue.value = ''
      await chatApi.sendMessage(
        liveSessionId,
        buildRequestPayload({
          goal: finalPrompt,
        }),
      )
    } catch (error) {
      isSending.value = false
      errorMessage.value = error.message || '发送消息失败'
    }
  }

  async function startNewSession() {
    if (sessionId.value && isLiveSession.value) {
      try {
        await chatApi.closeSession(sessionId.value)
      } catch (error) {
        // 会话关闭失败时保留当前界面，避免阻断下一次对话。
      }
    }
    disconnectEventSource()
    resetSessionView()
    await loadSessions()
  }

  // 工作区上下文切换后，实时会话不能继续复用旧目录，否则工具仍会在旧工作目录执行。
  async function resetSessionScope() {
    if (sessionId.value && isLiveSession.value) {
      try {
        await chatApi.closeSession(sessionId.value)
      } catch (error) {
        // 作用域切换优先保证前端状态正确，关闭旧会话失败时仍继续重置界面。
      }
    }
    disconnectEventSource()
    resetSessionView()
  }

  // 删除历史会话后，统一回退到剩余会话或欢迎态，避免界面停留在已失效的 sessionId。
  async function deleteSession(targetSessionId) {
    if (!historyEnabled || !targetSessionId) {
      return
    }
    const deletingSelectedSession = selectedSessionId.value === targetSessionId
    const deletingLiveSession = sessionId.value === targetSessionId && isLiveSession.value
    try {
      errorMessage.value = ''
      if (deletingLiveSession) {
        try {
          await chatApi.closeSession(targetSessionId)
        } catch (error) {
          // 历史删除仍应继续，避免运行时会话关闭失败阻断归档清理。
        }
      }
      await chatApi.deleteHistorySession(targetSessionId)
      archivedSessions.value = archivedSessions.value.filter((item) => item.sessionId !== targetSessionId)
      if (deletingSelectedSession || deletingLiveSession) {
        disconnectEventSource()
        resetSessionView()
        await loadSessions()
        if (archivedSessions.value.length > 0) {
          await selectSession(archivedSessions.value[0].sessionId)
        }
        return
      }
      await loadSessions(selectedSessionId.value)
    } catch (error) {
      errorMessage.value = error.message || '删除会话失败'
    }
  }

  async function clearSessions() {
    if (!historyEnabled) {
      disconnectEventSource()
      resetSessionView()
      return
    }
    try {
      errorMessage.value = ''
      if (sessionId.value && isLiveSession.value) {
        try {
          await chatApi.closeSession(sessionId.value)
        } catch (error) {
          // 清理归档优先，关闭实时会话失败时继续清理历史文件。
        }
      }
      await chatApi.clearHistorySessions()
      disconnectEventSource()
      resetSessionView()
      await loadSessions()
    } catch (error) {
      errorMessage.value = error.message || '清理会话失败'
    }
  }

  async function hydrateLatestHistory() {
    if (!historyEnabled) {
      return
    }
    await loadSessions()
    if (archivedSessions.value.length > 0) {
      await selectSession(archivedSessions.value[0].sessionId)
    }
  }

  onBeforeUnmount(() => {
    disconnectEventSource()
  })

  return {
    workingDirectory,
    inputValue,
    sessionId,
    selectedSessionId,
    isLiveSession,
    isLoadingSessions,
    isLoadingDetail,
    isSending,
    errorMessage,
    connectionStatus,
    historyEnabled,
    archivedSessions,
    messages,
    canSend,
    loadSessions,
    selectSession,
    sendPrompt,
    startNewSession,
    resetSessionScope,
    deleteSession,
    clearSessions,
    hydrateLatestHistory,
  }
}
