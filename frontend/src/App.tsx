import React from 'react'
import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
  useNavigate,
} from 'react-router-dom'

import AuthPage from './pages/auth/LoginPage'
import MainLayout from './layouts/MainLayout'
import Dashboard from './pages/dashboard/Dashboard'
import ComputersPage from './pages/clients/ComputersPage'
import ComputerDetailPage from './pages/clients/ComputerDetailPage'
import HistoryPage from './pages/history/HistoryPage'
import AlertsPage from './pages/alerts/AlertsPage'
import AgentsPage from './pages/agents/AgentsPage'
import RemoteDesktopPage from './pages/remote/RemoteDesktopPage'
import RemoteCommands from './pages/remote/RemoteCommands' 
import AccountManagementPage from './pages/settings/AccountManagementPage'
import SystemConfigurationPage from './pages/settings/SystemConfigurationPage'
import { ThemeProvider } from './context/ThemeContext'


import {
  clearSession,
  getSession,
  forgotPassword,
  login,
  register,
  resendVerification,
  resetPassword,
  saveSession,
  verifyEmail,
} from './lib/api'

// ==========================================
// ROUTE ĐĂNG NHẬP / ĐĂNG KÝ / QUÊN MẬT KHẨU
// ==========================================

function LoginRoute() {
  const navigate = useNavigate()
  const session = getSession()

  if (session) {
    return <Navigate to="/dashboard" replace />
  }

  return (
    <AuthPage
      onLogin={async ({ identifier, password, remember }) => {
        try {
          const sessionData = await login(identifier, password)
          saveSession(sessionData, remember)
          navigate('/dashboard', { replace: true })
        } catch (error) {
          console.error('Đăng nhập thất bại:', error)
          throw error
        }
      }}
      onRegister={async ({ name, email, password }) => {
        try {
          await register(name, email, password)
        } catch (error) {
          console.error('Đăng ký thất bại:', error)
          throw error
        }
      }}
      onForgotPassword={async (identifier) => {
        try {
          await forgotPassword(identifier)
        } catch (error) {
          console.error('Khôi phục mật khẩu thất bại:', error)
          throw error
        }
      }}
      onResetPassword={async ({ email, code, newPassword }) => {
        try {
          await resetPassword(email, code, newPassword)
        } catch (error) {
          console.error('Đặt lại mật khẩu thất bại:', error)
          throw error
        }
      }}
      onVerifyEmail={async ({ email, code }) => {
        try {
          await verifyEmail(email, code)
        } catch (error) {
          console.error('Xác thực email thất bại:', error)
          throw error
        }
      }}
      onResendVerification={async (email) => {
        try {
          await resendVerification(email)
        } catch (error) {
          console.error('Gửi lại mã xác thực thất bại:', error)
          throw error
        }
      }}
    />
  )
}

// ==========================================
// BẢO VỆ ROUTE (ROUTE GUARD CHO LAYOUT)
// ==========================================
function ProtectedLayout() {
  const session = getSession()

  if (!session) {
    return <Navigate to="/login" replace />
  }

  return <MainLayout />
}

// ==========================================
// APP ROUTER
// ==========================================

export default function App() {
  return (
    <ThemeProvider>
      <BrowserRouter>
        <Routes>

          {/* Trang mặc định */}
          <Route
            path="/"
            element={<Navigate to="/login" replace />}
          />

          {/* Trang đăng nhập và đăng ký */}
          <Route
            path="/login"
            element={<LoginRoute />}
          />

          {/* Các trang nằm trong khung quản trị chung (ProtectedLayout bọc MainLayout) */}
          <Route element={<ProtectedLayout />}>
            <Route
              path="/dashboard"
              element={<Dashboard />}
            />
            <Route
              path="/computers"
              element={<ComputersPage />}
            />
            <Route
              path="/computers/:id"
              element={<ComputerDetailPage />}
            />
            <Route
              path="/history"
              element={<HistoryPage />}
            />
            <Route
              path="/alerts"
              element={<AlertsPage />}
            />
            <Route
              path="/agents"
              element={<AgentsPage />}
            />
            <Route
              path="/remote-desktop"
              element={<RemoteDesktopPage />}
            />
            <Route
              path="/remote-commands"
              element={<RemoteCommands />}
            /> 

             <Route
              path="/settings/accounts"
              element={<AccountManagementPage />}
            />

             <Route
              path="/settings/system"
              element={<SystemConfigurationPage />}
            />
          </Route>

          {/* Đường dẫn không tồn tại */}
          <Route
            path="*"
            element={<Navigate to="/login" replace />}
          />

        </Routes>
      </BrowserRouter>
    </ThemeProvider>
  )
}