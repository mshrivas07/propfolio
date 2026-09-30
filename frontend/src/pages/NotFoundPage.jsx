import { Link } from 'react-router'

export default function NotFoundPage() {
  return (
    <main className="mx-auto max-w-md px-4 py-16">
      <h1 className="text-2xl font-semibold">Page not found</h1>
      <p className="mt-2 text-ink-muted">The address may be mistyped, or the page has moved.</p>
      <Link to="/" className="mt-6 inline-block text-action underline underline-offset-4">
        Go to the dashboard
      </Link>
    </main>
  )
}
