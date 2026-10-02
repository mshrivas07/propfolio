import { useEffect, useState } from 'react'
import ApiStatus from '../components/ApiStatus'
import { getMe } from '../lib/api'

export default function DashboardPage() {
  const [me, setMe] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    getMe()
      .then((data) => !cancelled && setMe(data))
      .catch((err) => !cancelled && setError(err.message))
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <section>
      <h1 className="text-2xl font-semibold">Dashboard</h1>
      {me && <p className="mt-2 text-ink-muted">Signed in as <span className="font-medium text-ink">{me.email}</span></p>}
      {error && (
        <p role="alert" className="mt-2 text-sm text-attention">
          Couldn't load your profile from the API. ({error})
        </p>
      )}
      <p className="mt-4 max-w-prose text-ink-muted">
        Invoices, amounts pending and due dates will appear here once billing is built.
      </p>
      <div className="mt-6">
        <ApiStatus />
      </div>
    </section>
  )
}
