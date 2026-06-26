import { fetchJson } from './http'

// 工作目录接口统一使用 POST，便于后续继续扩展批量操作。
export function createWorkspaceApi(baseUrl = '/api/workspace') {
  return {
    query(path = '') {
      return fetchJson(`${baseUrl}/query`, {
        method: 'POST',
        body: JSON.stringify({
          path,
        }),
      })
    },

    // 直接读取 JSON 文件内容，避免前端再从快照里手动提取 currentFile.content。
    queryJson(path = '') {
      return fetchJson(`${baseUrl}/query-json`, {
        method: 'POST',
        body: JSON.stringify({
          path,
        }),
      })
    },

    create(payload = {}) {
      return fetchJson(`${baseUrl}/create`, {
        method: 'POST',
        body: JSON.stringify(payload),
      })
    },

    createFile(path, content = '') {
      return this.create({
        path,
        directory: false,
        content,
      })
    },

    createDirectory(path) {
      return this.create({
        path,
        directory: true,
      })
    },

    update(payload = {}) {
      return fetchJson(`${baseUrl}/update`, {
        method: 'POST',
        body: JSON.stringify(payload),
      })
    },

    updateFileContent(path, content) {
      return this.update({
        path,
        content,
      })
    },

    rename(path, newName) {
      return this.update({
        path,
        newName,
      })
    },

    remove(path) {
      return fetchJson(`${baseUrl}/delete`, {
        method: 'POST',
        body: JSON.stringify({
          path,
        }),
      })
    },
  }
}
