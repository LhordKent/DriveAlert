import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'
import { AppShell } from '../layout/AppShell'
import { ProtectedRoute, PublicOnlyRoute } from './RouteGuards'
import { SignInPage } from '../../features/auth/SignInPage'
import { RegisterPage } from '../../features/auth/RegisterPage'
import { ForgotPasswordPage } from '../../features/auth/ForgotPasswordPage'
import { DashboardPage } from '../../features/dashboard/DashboardPage'
import { DriversPage } from '../../features/drivers/DriversPage'
import { DriverDetailPage } from '../../features/drivers/DriverDetailPage'
import { RecordDetailPage } from '../../features/records/RecordDetailPage'
import { RequestsPage } from '../../features/requests/RequestsPage'
import { InviteDriverPage } from '../../features/requests/InviteDriverPage'
import { AccountPage } from '../../features/account/AccountPage'
import { SettingsPage } from '../../features/settings/SettingsPage'
import { AboutPage } from '../../features/about/AboutPage'
import { NotFoundPage } from '../../features/about/NotFoundPage'

const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/dashboard" replace /> },
  {
    element: <PublicOnlyRoute />,
    children: [
      { path: '/auth/sign-in', element: <SignInPage /> },
      { path: '/auth/register', element: <RegisterPage /> },
      { path: '/auth/forgot-password', element: <ForgotPasswordPage /> },
    ],
  },
  {
    element: <ProtectedRoute />,
    children: [{
      element: <AppShell />,
      children: [
        { path: '/dashboard', element: <DashboardPage /> },
        { path: '/drivers', element: <DriversPage /> },
        { path: '/drivers/:driverId', element: <DriverDetailPage /> },
        { path: '/drivers/:driverId/records/:recordId', element: <RecordDetailPage /> },
        { path: '/requests', element: <RequestsPage /> },
        { path: '/requests/invite', element: <InviteDriverPage /> },
        { path: '/account', element: <AccountPage /> },
        { path: '/settings', element: <SettingsPage /> },
        { path: '/about', element: <AboutPage /> },
      ],
    }],
  },
  { path: '*', element: <NotFoundPage /> },
])

export function AppRouter() { return <RouterProvider router={router} /> }
