import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuthStore } from '../stores/authStore'

/**
 * Route guard: signed-out users are sent to /login, then returned here after signing in.
 */
export default function RequireAuth() {
  const session = useAuthStore((state) => state.session)
  const initialized = useAuthStore((state) => state.initialized)
  const location = useLocation()

  if (!initialized) {
    return <p className="p-8 text-sm text-ink-muted">Loading…</p>
  }
  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}
