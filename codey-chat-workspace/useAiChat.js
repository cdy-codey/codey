import { computed, onBeforeUnmount, ref } from 'vue'
import {
  extractAssistantContentFromFinalResult,
  extractFinalResultPayload,
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
  'human_confirmation_required',
  'verification',
  'task_status',
  'final_summary',
  'model_output',
  'debug_trace',
  'security_event',
]

// 复杂度关键词：命中后进度条估算时长会适当上浮，反映任务可能更耗时。
const COMPLEX_TASK_PATTERN = /批量|生成|填写|填表|校验|检查|合规|修改|重构|分析|编写|实现|开发|部署|测试|迁移|整理|转换|提取|导出|导入/

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
  const pendingToolCalls = ref([])
  const eventSource = ref(null)
  const activeAssistantId = ref('')
  const resumeContext = ref(null)
  // 进度条状态：估算总时长后随时间推进，未完成时最高停在 99%。
  const progress = ref(0)
  const progressVisible = ref(false)
  const progressLabel = ref('')
  let progressTimer = null
  let progressFinishTimer = null
  let progressEstimatedMs = 0
  let progressStartedAt = 0

  function isJsonParseLikeError(error) {
    const message = typeof error?.message === 'string' ? error.message.toLowerCase() : ''
    return !!(
      error instanceof SyntaxError
      || message.includes('json')
      && (
        message.includes('parse')
        || message.includes('unexpected token')
        || message.includes('unexpected end')
        || message.includes('unterminated')
      )
    )
  }

  function applyUserFacingError(error, fallbackMessage, scope = 'useAiChat') {
    if (isJsonParseLikeError(error)) {
      console.warn(`[${scope}] 捕获到 JSON 解析异常，已忽略界面提示:`, error)
      return
    }
    errorMessage.value = error?.message || fallbackMessage
  }

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

  function resolveSkillNamesOption(name) {
    const rawValue = typeof options?.[name] === 'function' ? options[name]() : options?.[name]
    if (Array.isArray(rawValue)) {
      return rawValue
        .flatMap((item) => (typeof item === 'string' ? item.split(',') : []))
        .map((item) => item.trim())
        .filter((item) => item.length > 0)
    }
    if (typeof rawValue === 'string') {
      return rawValue
        .split(',')
        .map((item) => item.trim())
        .filter((item) => item.length > 0)
    }
    return []
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
    const skillNames = resolveSkillNamesOption('skillName')
    const explicitSkillNames = Array.isArray(extra.skillNames) ? normalizeStringArray(extra.skillNames) : null
    const mergedSkillNames = explicitSkillNames && explicitSkillNames.length > 0 ? explicitSkillNames : skillNames
    const skillName = typeof extra.skillName === 'string'
      ? extra.skillName.trim()
      : (mergedSkillNames[0] || '')
    return {
      skillName,
      skillNames: mergedSkillNames,
      workingDirectory: workingDirectory.value,
      tenantId: context.tenantId || '',
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
    return rawValue.map((item) => {
      if (typeof item === 'string' && item.trim().length > 0) {
        const content = item.trim()
        return {
          label: content,
          content,
        }
      }
      if (!item || typeof item !== 'object' || Array.isArray(item)) {
        return null
      }
      const label = typeof item.label === 'string' ? item.label.trim() : ''
      const content = typeof item.content === 'string' ? item.content.trim() : ''
      if (!label || !content) {
        return null
      }
      return {
        label,
        content,
      }
    }).filter(Boolean)
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
    pendingToolCalls.value = []
    isSending.value = false
    stopProgress()
  }

  // 根据任务内容粗略估算总耗时，供进度条按时间推进（后端未提供精确耗时字段）。
  function estimateDurationMs(prompt) {
    const text = typeof prompt === 'string' ? prompt : ''
    const lengthMs = Math.min(4000, text.length * 80)
    const complexityMs = COMPLEX_TASK_PATTERN.test(text) ? 2000 : 0
    return Math.min(30000, 8000 + lengthMs + complexityMs)
  }

  function stopProgress() {
    if (progressTimer) {
      clearInterval(progressTimer)
      progressTimer = null
    }
    if (progressFinishTimer) {
      clearTimeout(progressFinishTimer)
      progressFinishTimer = null
    }
    progressVisible.value = false
    progress.value = 0
    progressLabel.value = ''
  }

  function startProgress(prompt) {
    stopProgress()
    progressEstimatedMs = estimateDurationMs(prompt)
    progressStartedAt = Date.now()
    progress.value = 0
    progressVisible.value = true
    progressLabel.value = '正在分析需求...'
    progressTimer = setInterval(tickProgress, 200)
  }

  function tickProgress() {
    if (!progressVisible.value) {
      return
    }
    const elapsed = Date.now() - progressStartedAt
    const ratio = Math.min(1, elapsed / progressEstimatedMs)
    // 减速曲线：快速逼近 99%，但完成事件到来前永不达到 100%。
    const target = 99 * (1 - Math.pow(1 - ratio, 2.5))
    progress.value = Math.min(99, Math.max(progress.value, Math.round(target)))
  }

  function noteProgress(label) {
    if (!progressVisible.value || !label) {
      return
    }
    progressLabel.value = label
  }

  function finishProgress() {
    if (progressTimer) {
      clearInterval(progressTimer)
      progressTimer = null
    }
    progress.value = 100
    progressLabel.value = '已完成'
    // 完成态短暂展示后收起，避免与最终正文抢占注意力。
    progressFinishTimer = setTimeout(() => {
      progressVisible.value = false
      progressFinishTimer = null
    }, 500)
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

  function refreshWelcomeMessages() {
    // 仅在尚未进入任何会话、界面仍停留在欢迎态时刷新欢迎文案，
    // 避免 openSystemAiAssistant 动态注入 assistantProps 后首次打开仍显示旧文案。
    const isWelcomeOnlyView =
      messages.value.length === 1
      && messages.value[0]?.role === 'assistant'
      && !sessionId.value
      && !selectedSessionId.value
      && !isLiveSession.value
      && !resumeContext.value
      && !isSending.value
      && !isLoadingDetail.value
    if (!isWelcomeOnlyView) {
      return
    }
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
    const message = createMessage('assistant', '', {
      live: true,
      toolCalls: pendingToolCalls.value.slice(),
    })
    messages.value.push(message)
    activeAssistantId.value = message.id
    pendingToolCalls.value = []
    return message
  }

  // 工具调用条目：{ name: 展示名, status: pending|running|done|failed }
  // 状态只向前推进，避免重复事件把已完成状态回退。
  const TOOL_CALL_STATUS_ORDER = { pending: 0, running: 1, done: 2, failed: 2 }

  function normalizeToolCall(item) {
    if (!item) {
      return null
    }
    if (typeof item === 'object') {
      const name = normalizeContent(item.name).trim()
      if (!name) {
        return null
      }
      return { name, status: item.status || 'done' }
    }
    const name = normalizeContent(item).trim()
    return name ? { name, status: 'done' } : null
  }

  function advanceToolCallStatus(current, next) {
    const currentOrder = TOOL_CALL_STATUS_ORDER[current] ?? -1
    const nextOrder = TOOL_CALL_STATUS_ORDER[next] ?? -1
    return nextOrder >= currentOrder ? next : current
  }

  function upsertToolCalls(list, items = []) {
    if (!Array.isArray(list)) {
      return
    }
    items.forEach((item) => {
      const normalized = normalizeToolCall(item)
      if (!normalized) {
        return
      }
      const existing = list.find((toolCall) => toolCall && toolCall.name === normalized.name)
      if (existing) {
        existing.status = advanceToolCallStatus(existing.status, normalized.status)
      } else {
        list.push(normalized)
      }
    })
  }

  function appendToolCalls(toolCalls = []) {
    const items = Array.isArray(toolCalls) ? toolCalls : [toolCalls]
    const normalized = items.map(normalizeToolCall).filter(Boolean)
    if (!normalized.length) {
      return
    }
    const currentId = activeAssistantId.value
    if (currentId) {
      const existing = messages.value.find((item) => item.id === currentId)
      if (existing) {
        upsertToolCalls(existing.toolCalls || [], normalized)
        return
      }
    }
    upsertToolCalls(pendingToolCalls.value, normalized)
  }

  // 把模型输出事件中的工具调用名合并到活动助手消息的 toolCalls 列表（去重 + 状态推进）
  function mergeToolCalls(message, toolCalls) {
    if (!message || !Array.isArray(toolCalls) || !toolCalls.length) {
      return
    }
    if (!Array.isArray(message.toolCalls)) {
      message.toolCalls = []
    }
    upsertToolCalls(message.toolCalls, toolCalls)
  }

  // 向聊天流中追加一条携带自定义卡片的助手消息，供业务层展示预览与确认操作。
  function appendAssistantCard(card) {
    const message = createMessage('assistant', '', { live: false })
    message.card = card
    messages.value.push(message)
    return message
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
      applyUserFacingError(error, '读取会话列表失败', 'useAiChat.loadSessions')
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
      applyUserFacingError(error, '读取会话详情失败', 'useAiChat.selectSession')
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
    noteProgress('已连接，正在处理...')
  }

  function handleModelTextDelta(payload) {
    const message = ensureActiveAssistantMessage()
    message.content += payload?.message || payload?.payload?.delta || ''
    noteProgress('正在生成内容...')
  }

  function handleModelThinkingDelta(payload) {
    const message = ensureActiveAssistantMessage()
    message.reasoning += payload?.message || payload?.payload?.delta || ''
    noteProgress('正在思考...')
  }

  function handleModelToolCallStarted(payload) {
    appendToolCalls([{ name: payload?.payload?.displayName || payload?.message, status: 'pending' }])
    noteProgress('正在准备工具调用...')
  }

  function handleToolExecutionStarted(payload) {
    const toolName = payload?.message || payload?.payload?.toolName || ''
    const displayName = payload?.payload?.displayName || toolName
    appendToolCalls([{ name: displayName || toolName, status: 'running' }])
    noteProgress('正在执行工具...')
    invokeHook('onToolExecutionStarted', {
      sessionId: sessionId.value,
      toolName,
      displayName,
      payload,
    })
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
      pendingToolCalls.value = []
      closeActiveAssistantMessage()
      isSending.value = false
      stopProgress()
      invokeHook('onTaskFailed', { sessionId: sessionId.value, message, payload })
    }
  }

  function handleFinalSummary(payload) {
    const message = ensureActiveAssistantMessage()
    const finalResult = extractFinalResultPayload(payload)
    const finalContent = extractAssistantContentFromFinalResult(finalResult)
    const summary = extractFinalSummaryText(payload)
    if (finalContent) {
      message.content = finalContent
    } else {
      message.content = normalizeFinalAssistantContent(message.content, summary)
    }
    const finalSummary = summary || message.content
    invokeHook('onAssistantFinished', {
      sessionId: sessionId.value,
      summary: finalSummary,
      content: message.content,
      finalResult,
      reasoning: message.reasoning,
      payload,
    })
    closeActiveAssistantMessage()
    isSending.value = false
    finishProgress()
    invokeHook('onFinalSummary', { sessionId: sessionId.value, summary: finalSummary, finalResult, payload })
    loadSessions(sessionId.value)
  }

  function handleToolCall(payload) {
    const toolName =
      payload?.payload?.request?.toolName ||
      payload?.payload?.request?.tool_name ||
      payload?.payload?.name ||
      ''
    const displayName =
      payload?.payload?.displayName ||
      payload?.payload?.request?.displayName ||
      toolName
    const success = payload?.payload?.result?.success !== false
    appendToolCalls([{ name: displayName || toolName, status: success ? 'done' : 'failed' }])
    noteProgress('正在处理工具结果...')
    invokeHook('onToolCall', { sessionId: sessionId.value, toolName, displayName, success, payload })
  }

  function handleVerificationOrHumanDecision(payload, eventType) {
    appendToolCalls([{ name: payload?.payload?.displayName || payload?.stage || payload?.message || eventType, status: 'done' }])
  }

  function handleHumanConfirmation(payload) {
    const inner = payload?.payload || {}
    return invokeAsyncHook('onHumanConfirmation', {
      sessionId: payload?.sessionId || sessionId.value,
      confirmationId: inner.confirmationId,
      toolName: inner.toolName,
      arguments: inner.arguments,
      summary: inner.summary,
      uncertaintyReason: inner.uncertaintyReason,
      payload,
    })
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
      message.content = normalizeStructuredAssistantContent(parsedOutput.content) || parsedOutput.content
    }
    mergeToolCalls(message, parsedOutput.toolCalls)
    // 工具调用轮次不再关闭当前消息：一轮对话内所有输出（含多轮工具调用的中间文本）
    // 聚合到同一个 AI 气泡，避免每次 finishReason=tool_calls 截断导致同一轮出现多个气泡。
    // 消息统一由 final_summary 收口关闭。
  }

  function normalizeStructuredAssistantContent(content) {
    const normalized = normalizeContent(content).trim()
    if (!normalized.startsWith('{')) {
      return ''
    }
    try {
      const parsed = JSON.parse(normalized)
      return extractAssistantContentFromFinalResult(parsed)
    } catch (error) {
      return ''
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
    human_confirmation_required: handleHumanConfirmation,
    security_event: handleSecurityEvent,
    model_output: handleModelOutput,
    // 以下事件类型只需记录或无需处理
    tool_execution_started: handleToolExecutionStarted,
    debug_trace: () => {},
    verification: (payload, eventType) => handleVerificationOrHumanDecision(payload, eventType),
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

  async function ensureSessionReady(firstPrompt = '') {
    return ensureLiveSession(firstPrompt)
  }

  async function sendPrompt(prompt = inputValue.value) {
    const goal = normalizeContent(prompt).trim()
    if (!goal || isSending.value) {
      return
    }

    try {
      errorMessage.value = ''
      isSending.value = true
      startProgress(goal)
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
      stopProgress()
      applyUserFacingError(error, '发送消息失败', 'useAiChat.sendPrompt')
    }
  }

  // 用户点击选项后，直接将选项文本作为用户输入发送给 AI
  async function submitChoice(optionLabel, note = '') {
    const label = normalizeContent(optionLabel).trim()
    if (!label || isSending.value) {
      return
    }
    const normalizedNote = normalizeContent(note).trim()

    try {
      errorMessage.value = ''
      isSending.value = true
      startProgress(label)
      const liveSessionId = await ensureLiveSession(label)

      // 聊天中显示选项文本，有备注时附加
      const displayText = normalizedNote
        ? `${label}（${normalizedNote}）`
        : label
      messages.value.push(createMessage('user', displayText))
      syncLiveSessionSummary(displayText)
      inputValue.value = ''
      // 直接将选项内容和备注发送给 AI
      const goal = normalizedNote ? `${label}\n备注: ${normalizedNote}` : label
      await chatApi.sendMessage(
        liveSessionId,
        buildRequestPayload({
          goal,
        }),
      )
    } catch (error) {
      isSending.value = false
      stopProgress()
      applyUserFacingError(error, '发送选项失败', 'useAiChat.submitChoice')
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
      applyUserFacingError(error, '删除会话失败', 'useAiChat.deleteSession')
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
      applyUserFacingError(error, '清理会话失败', 'useAiChat.clearSessions')
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
    stopProgress()
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
    progress,
    progressVisible,
    progressLabel,
    errorMessage,
    connectionStatus,
    historyEnabled,
    archivedSessions,
    messages,
    canSend,
    loadSessions,
    selectSession,
    sendPrompt,
    submitChoice,
    ensureSessionReady,
    startNewSession,
    resetSessionScope,
    deleteSession,
    clearSessions,
    hydrateLatestHistory,
    refreshWelcomeMessages,
    appendAssistantCard,
  }
}
