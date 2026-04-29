import axios from 'axios'

const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

const api = axios.create({
  baseURL: API_BASE,
  headers: { 'Content-Type': 'application/json' },
})

// Attach token from localStorage on every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('lendai_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// On 401, clear auth and redirect
api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err?.response?.status === 401) {
      localStorage.removeItem('lendai_token')
      localStorage.removeItem('lendai_user')
      if (window.location.pathname !== '/login' && window.location.pathname !== '/register') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(err)
  }
)

// Auth
export const loginUser = (email, password) =>
  api.post('/auth/login', { email, password })

export const registerUser = (name, email, password, role) =>
  api.post('/auth/register', { name, email, password, role })

// Loan
export const applyLoan = (payload) =>
  api.post('/loan/apply', payload)

// CSV-based prediction: only loanAmount needed (userId from JWT + 1000)
export const predictLoan = (loanAmount) =>
  api.post('/loan/predict', { loanAmount })

// Fetch user profile from CSV (auto maps JWT userId + 1000)
export const getCsvProfile = () =>
  api.get('/loan/csv-profile')

export const getLoanHistory = () =>
  api.get('/loan/history/me')

// Audit
export const getAuditLogs = () =>
  api.get('/audit/logs')

export const getAuditDetail = (applicationId) =>
  api.get(`/audit/logs/${applicationId}`)

export const getAuditStats = () =>
  api.get('/audit/stats')

export const verifyAuditChain = () =>
  api.get('/audit/verify')

// Reports
export const getFairnessReport = () =>
  api.get('/reports/fairness')

export const getCsvStats = () =>
  api.get('/reports/csv-stats')

export const getFairnessHistory = (startDate, endDate, attribute) =>
  api.get('/reports/fairness/history', { params: { startDate, endDate, attribute } })

export default api
export { API_BASE }