import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import { ApiError } from '@/api/client'
import App from './App'
import { AuthProvider } from '@/features/auth/AuthContext'
import './styles.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // A 401/403/404 will not become a different answer by asking again; retrying them only
      // delays the error the user needs to see. Everything else gets one retry.
      retry: (failureCount, error) => {
        if (error instanceof ApiError && [401, 403, 404, 409].includes(error.status)) return false
        return failureCount < 1
      },
      staleTime: 15_000,
      refetchOnWindowFocus: false,
    },
  },
})

const container = document.getElementById('root')
if (!container) throw new Error('#root not found')

createRoot(container).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      {/* Opting into the v7 behaviours now keeps the eventual React Router upgrade a
          version bump rather than a migration. */}
      <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
