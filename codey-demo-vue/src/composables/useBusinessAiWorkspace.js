import { createWorkspaceJsonResource } from './useWorkspaceJsonResource'

/**
 * 业务页面统一通过 context.json 与 AI 交互，便于以后扩展更多业务场景。
 */
export function createBusinessAiWorkspace(options = {}) {
  const workspaceId = typeof options.workspaceId === 'string' ? options.workspaceId.trim() : ''
  const workingDirectory = typeof options.workingDirectory === 'string' ? options.workingDirectory.trim() : ''
  const contextFileKey = typeof options.contextFileKey === 'string' && options.contextFileKey.trim()
    ? options.contextFileKey.trim()
    : 'context.json'

  if (!workspaceId) {
    throw new Error('workspaceId 不能为空')
  }
  if (!workingDirectory) {
    throw new Error('workingDirectory 不能为空')
  }

  const contextWorkspacePath = `${workspaceId}/${contextFileKey}`
  const contextResource = createWorkspaceJsonResource({
    workspacePath: contextWorkspacePath,
  })

  async function syncContext(payload) {
    return contextResource.save(payload)
  }

  async function loadContext(options = {}) {
    return contextResource.load({
      fallback: null,
      ...options,
    })
  }

  async function prepareForSend(contextPayload) {
    await syncContext(contextPayload)
  }

  return {
    workspaceId,
    workingDirectory,
    contextFileKey,
    contextDocumentId: `workspace:${contextFileKey}`,
    contextWorkspacePath,
    loadContext,
    syncContext,
    prepareForSend,
  }
}
