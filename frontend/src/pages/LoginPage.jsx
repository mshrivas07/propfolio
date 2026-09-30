import { useState } from 'react'

/**
 * Sign-in screen shell. Phase 1 connects it to Supabase Auth.
 */
export default function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [notice, setNotice] = useState('')

  function handleSubmit(event) {
    event.preventDefault()
    setNotice('Sign-in is connected in Phase 1. Nothing was sent.')
  }

  return (
    <main className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm rounded-lg border border-line bg-panel p-6">
        <h1 className="text-xl font-semibold">Sign in to Propfolio</h1>
        <p className="mt-1 text-sm text-ink-muted">Split utility bills and invoice your tenants.</p>

        <form onSubmit={handleSubmit} className="mt-6 space-y-4" noValidate>
          <div>
            <label htmlFor="email" className="block text-sm font-medium">Email</label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="mt-1 w-full rounded-md border border-line px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label htmlFor="password" className="block text-sm font-medium">Password</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="mt-1 w-full rounded-md border border-line px-3 py-2 text-sm"
            />
          </div>
          <button
            type="submit"
            className="w-full rounded-md bg-action px-4 py-2 text-sm font-medium text-white hover:bg-action-strong"
          >
            Sign in
          </button>
          {notice && <p role="status" className="text-sm text-attention">{notice}</p>}
        </form>
      </div>
    </main>
  )
}
