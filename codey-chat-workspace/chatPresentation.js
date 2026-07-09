// 把普通文本和 fenced code block 拆开渲染，让回复更接近文档阅读体验。
export function getMessageBlocks(content) {
  const source = typeof content === 'string' ? content : ''
  if (!source.trim()) {
    return []
  }
  const blocks = []
  const pattern = /```([\w-]+)?\n?([\s\S]*?)```/g
  let lastIndex = 0
  let match

  while ((match = pattern.exec(source)) !== null) {
    const textSegment = source.slice(lastIndex, match.index)
    pushTextBlock(blocks, textSegment)
    blocks.push({
      type: 'code',
      language: (match[1] || '').trim(),
      content: (match[2] || '').replace(/\n$/, ''),
    })
    lastIndex = pattern.lastIndex
  }

  pushTextBlock(blocks, source.slice(lastIndex))
  return blocks
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
