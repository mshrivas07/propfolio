import { useEffect } from 'react'
import { createBrowserRouter, RouterProvider } from 'react-router'
import RequireAuth from './components/RequireAuth'
import AppLayout from './layouts/AppLayout'
import ComingSoonPage from './pages/ComingSoonPage'
import DashboardPage from './pages/DashboardPage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
import BillDetailPage from './pages/bills/BillDetailPage'
import BillsPage from './pages/bills/BillsPage'
import PropertiesPage from './pages/properties/PropertiesPage'
import PropertyDetailPage from './pages/properties/PropertyDetailPage'
import TenantsPage from './pages/tenants/TenantsPage'
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
          { path: 'properties', element: <PropertiesPage /> },
          { path: 'properties/:propertyId', element: <PropertyDetailPage /> },
          { path: 'tenants', element: <TenantsPage /> },
          { path: 'bills', element: <BillsPage /> },
          { path: 'bills/:billId', element: <BillDetailPage /> },
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
