import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { isSupabaseConfigured, supabase } from '../lib/supabase'
import { useAuthStore } from '../stores/authStore'

const MIN_PASSWORD_LENGTH = 8

/**
 * Sign in or create an account with Supabase email + password.
 */
export default function LoginPage() {
  const session = useAuthStore((state) => state.session)
  const navigate = useNavigate()
  const location = useLocation()
  const returnTo = location.state?.from ?? '/'

  const [mode, setMode] = useState('signin')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  if (session) {
    return <Navigate to={returnTo} replace />
  }

  const isSignUp = mode === 'signup'

  function switchMode(next) {
    setMode(next)
    setError('')
    setNotice('')
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setNotice('')

    if (!email || !password) {
      setError('Enter your email and password.')
      return
    }
    if (isSignUp && password.length < MIN_PASSWORD_LENGTH) {
      setError(`Use at least ${MIN_PASSWORD_LENGTH} characters for your password.`)
      return
    }

    setBusy(true)
    try {
      if (isSignUp) {
        const { data, error: signUpError } = await supabase.auth.signUp({
          email,
          password,
          options: { emailRedirectTo: window.location.origin },
        })
        if (signUpError) throw signUpError
        if (!data.session) {
          setNotice('Check your inbox and confirm your email, then sign in.')
          switchModeKeepNotice()
        }
      } else {
        const { error: signInError } = await supabase.auth.signInWithPassword({ email, password })
        if (signInError) throw signInError
        navigate(returnTo, { replace: true })
      }
    } catch (err) {
      setError(err.message ?? 'Something went wrong. Try again.')
    } finally {
      setBusy(false)
    }
  }

  function switchModeKeepNotice() {
    setMode('signin')
    setPassword('')
  }

  return (
    <main className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm rounded-lg border border-line bg-panel p-6">
        <h1 className="text-xl font-semibold">{isSignUp ? 'Create your Propfolio account' : 'Sign in to Propfolio'}</h1>
        <p className="mt-1 text-sm text-ink-muted">Split utility bills and invoice your tenants.</p>

        {!isSupabaseConfigured ? (
          <p role="alert" className="mt-6 text-sm text-attention">
            Sign-in isn't set up yet. Add VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY to frontend/.env and restart
            <code className="mx-1">npm run dev</code>.
          </p>
        ) : (
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
                autoComplete={isSignUp ? 'new-password' : 'current-password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="mt-1 w-full rounded-md border border-line px-3 py-2 text-sm"
              />
              {isSignUp && (
                <p className="mt-1 text-xs text-ink-muted">At least {MIN_PASSWORD_LENGTH} characters.</p>
              )}
            </div>
            <button
              type="submit"
              disabled={busy}
              className="w-full rounded-md bg-action px-4 py-2 text-sm font-medium text-white hover:bg-action-strong disabled:opacity-60"
            >
              {busy ? 'Please wait…' : isSignUp ? 'Create account' : 'Sign in'}
            </button>
            {error && <p role="alert" className="text-sm text-attention">{error}</p>}
            {notice && <p role="status" className="text-sm text-paid">{notice}</p>}
          </form>
        )}

        {isSupabaseConfigured && (
          <p className="mt-6 text-sm text-ink-muted">
            {isSignUp ? 'Already have an account?' : 'New to Propfolio?'}{' '}
            <button
              type="button"
              onClick={() => switchMode(isSignUp ? 'signin' : 'signup')}
              className="text-action underline underline-offset-4"
            >
              {isSignUp ? 'Sign in' : 'Create an account'}
            </button>
          </p>
        )}
      </div>
    </main>
  )
}
