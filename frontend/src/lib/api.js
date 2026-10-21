import { config } from './config'
import { useAuthStore } from '../stores/authStore'

/**
 * Error from the API. For 400s, `fieldErrors` maps a field name (e.g. "name", "emails[1]")
 * to its message, so forms can show it under the right input.
 */
export class ApiError extends Error {
  constructor(message, status, fieldErrors = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

async function request(method, path, body) {
  const token = useAuthStore.getState().session?.access_token
  const headers = { Accept: 'application/json, application/problem+json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let response
  try {
    response = await fetch(`${config.apiBaseUrl}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError("Can't reach the server. Check that the backend is running.", 0)
  }

  if (response.status === 401 && token) {
    await useAuthStore.getState().signOut()
  }
  if (response.status === 204) return null

  const text = await response.text()
  const data = text ? JSON.parse(text) : null

  if (!response.ok) {
    const fieldErrors = Object.fromEntries((data?.errors ?? []).map((e) => [e.field, e.message]))
    const message = data?.detail ?? `Request failed (${response.status})`
    throw new ApiError(message, response.status, fieldErrors)
  }
  return data
}

export const apiGet = (path) => request('GET', path)
const apiPost = (path, body) => request('POST', path, body)
const apiPut = (path, body) => request('PUT', path, body)
const apiDelete = (path) => request('DELETE', path)

export const getHealth = () => apiGet('/api/health')
export const getMe = () => apiGet('/api/me')

// Properties (detail includes units, leases, utility accounts and split rules)
export const listProperties = () => apiGet('/api/properties')
export const getProperty = (id) => apiGet(`/api/properties/${id}`)
export const createProperty = (body) => apiPost('/api/properties', body)
export const updateProperty = (id, body) => apiPut(`/api/properties/${id}`, body)
export const deleteProperty = (id) => apiDelete(`/api/properties/${id}`)

// Units
export const createUnit = (propertyId, body) => apiPost(`/api/properties/${propertyId}/units`, body)
export const updateUnit = (id, body) => apiPut(`/api/units/${id}`, body)
export const deleteUnit = (id) => apiDelete(`/api/units/${id}`)

// Leases
export const createLease = (unitId, body) => apiPost(`/api/units/${unitId}/leases`, body)
export const updateLease = (id, body) => apiPut(`/api/leases/${id}`, body)
export const deleteLease = (id) => apiDelete(`/api/leases/${id}`)

// Utility accounts
export const createUtilityAccount = (propertyId, body) =>
  apiPost(`/api/properties/${propertyId}/utility-accounts`, body)
export const updateUtilityAccount = (id, body) => apiPut(`/api/utility-accounts/${id}`, body)
export const deleteUtilityAccount = (id) => apiDelete(`/api/utility-accounts/${id}`)

// Split rules (PUT creates or replaces the rule for a unit + account)
export const saveSplitRule = (body) => apiPut('/api/split-rules', body)
export const deleteSplitRule = (id) => apiDelete(`/api/split-rules/${id}`)

// Tenants
export const listTenants = () => apiGet('/api/tenants')
export const createTenant = (body) => apiPost('/api/tenants', body)
export const updateTenant = (id, body) => apiPut(`/api/tenants/${id}`, body)
export const deleteTenant = (id) => apiDelete(`/api/tenants/${id}`)
