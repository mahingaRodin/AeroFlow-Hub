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

export interface LoginResult {
  accessToken: string | null
  mfaRequired: boolean
  challengeToken: string | null
}

export interface TotpSetup {
  secret: string
  otpauthUri: string
  expiresAt: string
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

async function publicRequest<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })

  if (!response.ok) {
    let message = `Request failed (${response.status})`
    try {
      const body = (await response.json()) as { detail?: string; message?: string }
      message = body.detail || body.message || message
    } catch {
      // Some authentication errors have no JSON body.
    }
    throw new ApiError(message, response.status)
  }

  return (await response.json()) as T
}

export function login(email: string, password: string): Promise<LoginResult> {
  return publicRequest('/auth/login', { email, password })
}

export function registerCustomer(email: string, password: string): Promise<{ message: string }> {
  return publicRequest('/auth/register', { email, password })
}

export function verifyCustomerEmail(token: string): Promise<{ message: string }> {
  return publicRequest('/auth/verify-email', { token })
}

export function completeMfaLogin(challengeToken: string, code: string): Promise<LoginResult> {
  return publicRequest('/auth/mfa/login', { challengeToken, code })
}

export function getMfaStatus(token: string): Promise<{ totpEnabled: boolean }> {
  return request('/auth/mfa/status', token)
}

export function beginTotpSetup(
  token: string,
  currentPassword: string,
): Promise<TotpSetup> {
  return request('/auth/mfa/totp/setup', token, {
    method: 'POST',
    body: JSON.stringify({ currentPassword }),
  })
}

export function confirmTotpSetup(
  token: string,
  code: string,
): Promise<{ recoveryCodes: string[] }> {
  return request('/auth/mfa/totp/confirm', token, {
    method: 'POST',
    body: JSON.stringify({ code }),
  })
}

export function disableTotp(
  token: string,
  currentPassword: string,
  code: string,
): Promise<void> {
  return request('/auth/mfa/totp/disable', token, {
    method: 'POST',
    body: JSON.stringify({ currentPassword, code }),
  })
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
