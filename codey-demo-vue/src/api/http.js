const DEFAULT_HEADERS = {
  'Content-Type': 'application/json',
}

export async function fetchJson(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      ...DEFAULT_HEADERS,
      ...(options.headers || {}),
    },
  })

  const body = await readResponseBody(response)
  if (!response.ok) {
    throw new Error(readErrorMessage(body, response))
  }

  return unwrapResponseBody(body)
}

export function get(url, options = {}) {
  return fetchJson(url, {
    ...options,
    method: 'GET',
  })
}

export function post(url, body, options = {}) {
  return fetchJson(url, {
    ...options,
    method: 'POST',
    body: body == null || typeof body === 'string' ? body : JSON.stringify(body),
  })
}

async function readResponseBody(response) {
  if (response.status === 204) {
    return null
  }
  try {
    return await response.json()
  } catch (error) {
    return null
  }
}

function unwrapResponseBody(body) {
  if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'success')) {
    return body.data ?? null
  }
  return body
}

function readErrorMessage(body, response) {
  if (body?.message) {
    return body.message
  }
  if (body?.error) {
    return body.error
  }
  return response.statusText || `请求失败：${response.status}`
}
