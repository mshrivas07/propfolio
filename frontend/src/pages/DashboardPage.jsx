import ApiStatus from '../components/ApiStatus'

export default function DashboardPage() {
  return (
    <section>
      <h1 className="text-2xl font-semibold">Dashboard</h1>
      <p className="mt-2 max-w-prose text-ink-muted">
        Invoices, amounts pending and due dates will appear here once billing is built.
      </p>
      <div className="mt-6">
        <ApiStatus />
      </div>
    </section>
  )
}
