import { config } from './config'
import { useAuthStore } from '../stores/authStore'

export class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.status = status
  }
}

/**
 * Fetch wrapper for the Spring Boot API. Adds the Supabase access token when signed in.
 * A 401 means the session is no longer valid, so the user is signed out.
 */
export async function apiGet(path) {
  const token = useAuthStore.getState().session?.access_token
  const headers = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(`${config.apiBaseUrl}${path}`, { headers })

  if (response.status === 401 && token) {
    await useAuthStore.getState().signOut()
  }
  if (!response.ok) {
    throw new ApiError(`GET ${path} failed with status ${response.status}`, response.status)
  }
  return response.json()
}

export function getHealth() {
  return apiGet('/api/health')
}

export function getMe() {
  return apiGet('/api/me')
}
