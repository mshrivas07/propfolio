import { createBrowserRouter, RouterProvider } from 'react-router'
import AppLayout from './layouts/AppLayout'
import DashboardPage from './pages/DashboardPage'
import ComingSoonPage from './pages/ComingSoonPage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'

// Route guard for signed-in pages arrives in Phase 1.
const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
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
  { path: '*', element: <NotFoundPage /> },
])

export default function App() {
  return <RouterProvider router={router} />
}
