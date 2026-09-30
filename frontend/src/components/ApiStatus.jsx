import { useEffect, useState } from 'react'
import { getHealth } from '../lib/api'

/**
 * Shows whether the frontend can reach the Spring Boot API.
 */
export default function ApiStatus() {
  const [state, setState] = useState({ status: 'checking' })

  useEffect(() => {
    let cancelled = false
    getHealth()
      .then((data) => !cancelled && setState({ status: 'up', data }))
      .catch((error) => !cancelled && setState({ status: 'down', error: error.message }))
    return () => {
      cancelled = true
    }
  }, [])

  if (state.status === 'checking') {
    return <p className="text-sm text-ink-muted">Checking the API…</p>
  }

  if (state.status === 'down') {
    return (
      <div role="alert" className="rounded-md border border-attention/40 bg-panel p-4 text-sm">
        <p className="font-medium text-attention">Can't reach the API.</p>
        <p className="mt-1 text-ink-muted">
          Start the backend with <code>mvn spring-boot:run</code> in <code>/backend</code>, then refresh.
          ({state.error})
        </p>
      </div>
    )
  }

  return (
    <div className="rounded-md border border-line bg-panel p-4 text-sm">
      <p className="font-medium text-paid">API connected</p>
      <p className="mt-1 text-ink-muted">
        {state.data.service} responded at {new Date(state.data.timestamp).toLocaleString()}
      </p>
    </div>
  )
}
