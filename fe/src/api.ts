import type {
  CreateTravelClearanceRequest,
  TravelClearanceRequest,
} from './types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

async function request<T>(
  path: string,
  token: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${token}`,
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...init.headers,
    },
  })

  if (!response.ok) {
    let message = `Request failed (${response.status})`
    try {
      const body = (await response.json()) as { detail?: string; message?: string }
      message = body.detail || body.message || message
    } catch {
      // Some Spring Security responses have no JSON body.
    }
    throw new ApiError(message, response.status)
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export async function login(username: string, password: string): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })

  if (!response.ok) {
    let message = 'Unable to sign in. Check your credentials and try again.'
    try {
      const body = (await response.json()) as { detail?: string; message?: string }
      message = body.detail || body.message || message
    } catch {
      // Keep a useful sign-in message when the server returns an empty response.
    }
    throw new ApiError(message, response.status)
  }

  const body = (await response.json()) as { accessToken: string }
  return body.accessToken
}

export function listRequests(token: string): Promise<TravelClearanceRequest[]> {
  return request('/travel-clearance-requests', token)
}

export function createRequest(
  token: string,
  payload: CreateTravelClearanceRequest,
): Promise<TravelClearanceRequest> {
  return request('/travel-clearance-requests', token, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function submitRequest(
  token: string,
  id: string,
): Promise<TravelClearanceRequest> {
  return request(`/travel-clearance-requests/${encodeURIComponent(id)}/submit`, token, {
    method: 'POST',
  })
}
