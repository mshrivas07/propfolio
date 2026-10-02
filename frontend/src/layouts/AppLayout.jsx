import { NavLink, Outlet } from 'react-router'
import { useAuthStore } from '../stores/authStore'

const navItems = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/properties', label: 'Properties' },
  { to: '/bills', label: 'Bills' },
  { to: '/invoices', label: 'Invoices' },
]

function navClass({ isActive }) {
  const base = 'block whitespace-nowrap rounded-md px-3 py-2 text-sm transition-colors'
  return isActive
    ? `${base} bg-action text-white`
    : `${base} text-ink-muted hover:bg-surface hover:text-ink`
}

export default function AppLayout() {
  const email = useAuthStore((state) => state.session?.user?.email)
  const signOut = useAuthStore((state) => state.signOut)

  return (
    <div className="min-h-screen md:flex">
      <aside className="flex flex-col border-b border-line bg-panel md:min-h-screen md:w-56 md:border-b-0 md:border-r">
        <div className="flex items-center justify-between px-4 py-4">
          <p className="text-base font-semibold">Propfolio</p>
          <button
            type="button"
            onClick={signOut}
            className="text-sm text-ink-muted underline-offset-4 hover:text-ink hover:underline md:hidden"
          >
            Sign out
          </button>
        </div>
        <nav aria-label="Main" className="flex gap-1 overflow-x-auto px-2 pb-3 md:block md:space-y-1 md:pb-0">
          {navItems.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className={navClass}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="mt-auto hidden border-t border-line px-4 py-4 md:block">
          <p className="truncate text-xs text-ink-muted" title={email}>{email}</p>
          <button
            type="button"
            onClick={signOut}
            className="mt-2 text-sm text-ink-muted underline-offset-4 hover:text-ink hover:underline"
          >
            Sign out
          </button>
        </div>
      </aside>

      <main className="flex-1 px-4 py-6 md:px-10 md:py-8">
        <div className="mx-auto max-w-5xl">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
