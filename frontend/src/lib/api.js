import { config } from './config'

/**
 * Thin fetch wrapper for the Spring Boot API.
 * Phase 1 adds the Supabase JWT as a Bearer token here.
 */
export async function apiGet(path) {
  const response = await fetch(`${config.apiBaseUrl}${path}`, {
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) {
    throw new Error(`GET ${path} failed with status ${response.status}`)
  }
  return response.json()
}

export function getHealth() {
  return apiGet('/api/health')
}
