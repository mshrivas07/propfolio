import { useEffect } from 'react'
import { createBrowserRouter, RouterProvider } from 'react-router'
import RequireAuth from './components/RequireAuth'
import AppLayout from './layouts/AppLayout'
import ComingSoonPage from './pages/ComingSoonPage'
import DashboardPage from './pages/DashboardPage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
import { useAuthStore } from './stores/authStore'

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        path: '/',
        element: <AppLayout />,
        children: [
          { index: true, element: <DashboardPage /> },
          { path: 'properties', element: <ComingSoonPage title="Properties" phase={2} /> },
          { path: 'bills', element: <ComingSoonPage title="Bills" phase={3} /> },
          { path: 'invoices', element: <ComingSoonPage title="Invoices" phase={5} /> },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
])

export default function App() {
  const init = useAuthStore((state) => state.init)

  useEffect(() => init(), [init])

  return <RouterProvider router={router} />
}
