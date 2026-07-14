// 把普通文本和 fenced code block 拆开渲染，让回复更接近文档阅读体验。
export function getMessageBlocks(content, status) {
  const source = typeof content === 'string' ? content : ''
  if (!source.trim()) {
    return []
  }
  
  const isFinished = status === 'FINISH' || status === 'ERROR' || status === 'STOP' || status === 'CANCELED'

  // 容错处理：如果大模型直接输出了裸的 JSON 字符串（没有使用 ```json 包裹），进行特殊拦截
  const trimmedSource = source.trim()
  if (trimmedSource.startsWith('{') && (trimmedSource.includes('"_view_type"') || trimmedSource.includes('"view"') || trimmedSource.includes("'_view_type'"))) {
    try {
      const parsedView = resolveUiViewPayload(JSON.parse(trimmedSource))
      if (parsedView) {
        return buildUiViewBlocks(parsedView, trimmedSource)
      }
    } catch (e) {
      if (!isFinished) {
        // 解析失败说明还在流式输出中
        return [{
          type: 'code',
          language: 'json',
          content: trimmedSource,
          parsedUiView: { streaming: true },
          streaming: true
        }]
      } else {
        // 已结束但解析失败，当作普通代码块展示错误内容，不再 loading
        return [{
          type: 'code',
          language: 'json',
          content: trimmedSource
        }]
      }
    }
  }

  const blocks = []
  const pattern = /```([\w-]+)?\n?([\s\S]*?)```/g
  let lastIndex = 0
  let match

  while ((match = pattern.exec(source)) !== null) {
    const textSegment = source.slice(lastIndex, match.index)
    pushTextBlock(blocks, textSegment)
    const language = (match[1] || '').trim()
    const content = (match[2] || '').replace(/\n$/, '')
    let parsedUiView = null
    if (language === 'json') {
      try {
        parsedUiView = resolveUiViewPayload(JSON.parse(content))
      } catch (e) {
        // ignore
      }
    }

    blocks.push({
      type: 'code',
      language,
      content,
      parsedUiView,
    })
    lastIndex = pattern.lastIndex
  }

  const remaining = source.slice(lastIndex)
  if (remaining) {
    const unclosedPattern = /```([\w-]+)?\n?([\s\S]*)$/
    const unclosedMatch = unclosedPattern.exec(remaining)
    if (unclosedMatch) {
      const textSegment = remaining.slice(0, unclosedMatch.index)
      pushTextBlock(blocks, textSegment)
      
      const language = (unclosedMatch[1] || '').trim()
      const unclosedContent = unclosedMatch[2] || ''
      
      let parsedUiView = null
      if (language === 'json' && unclosedContent.includes('"_view_type"')) {
        if (!isFinished) {
          parsedUiView = { streaming: true }
        } else {
          // 如果已结束，尝试容错解析
          try {
            parsedUiView = resolveUiViewPayload(JSON.parse(unclosedContent))
          } catch(e) {
             // 保持 null，展示原码
          }
        }
      }
      
      blocks.push({
        type: 'code',
        language,
        content: unclosedContent,
        parsedUiView,
        streaming: !isFinished
      })
    } else {
      pushTextBlock(blocks, remaining)
    }
  }
  return blocks
}

function buildUiViewBlocks(view, rawContent) {
  if (!view || typeof view !== 'object') {
    return []
  }
  if (view._view_type === 'text') {
    return [{
      type: 'text',
      paragraphs: [typeof view.content === 'string' ? view.content : ''].filter(Boolean),
    }]
  }
  return [{
    type: 'code',
    language: 'json',
    content: rawContent,
    parsedUiView: view,
  }]
}

function resolveUiViewPayload(parsed) {
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return null
  }
  if (parsed._view_type) {
    return parsed
  }
  if (parsed.view && typeof parsed.view === 'object' && !Array.isArray(parsed.view) && parsed.view._view_type) {
    return parsed.view
  }
  return null
}

function pushTextBlock(blocks, content) {
  const normalized = typeof content === 'string' ? content.trim() : ''
  if (!normalized) {
    return
  }
  blocks.push({
    type: 'text',
    paragraphs: normalized.split(/\n{2,}/).map((item) => item.trim()).filter(Boolean),
  })
}

export function formatTime(value) {
  if (!value) {
    return '--'
  }
  let normalizedValue = value
  if (typeof normalizedValue === 'number' && normalizedValue < 1000000000000) {
    normalizedValue *= 1000
  }
  const date = new Date(normalizedValue)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', {
    hour12: false,
  })
}
