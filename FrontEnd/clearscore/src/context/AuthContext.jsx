/* @refresh reload */
import { createContext, useContext, useState, useCallback } from 'react'
import { loginUser, registerUser } from '../api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [token, setToken] = useState(localStorage.getItem('lendai_token') || '')
  const [user, setUser] = useState(() => {
    try { return JSON.parse(localStorage.getItem('lendai_user')) } catch { return null }
  })

  const isAuthenticated = !!token

  const login = useCallback(async (email, password) => {
    const res = await loginUser(email, password)
    const data = res.data
    const session = { userId: data.userId, name: data.name, email: data.email, role: data.role }
    setToken(data.accessToken)
    setUser(session)
    localStorage.setItem('lendai_token', data.accessToken)
    localStorage.setItem('lendai_user', JSON.stringify(session))
    return session
  }, [])

  const register = useCallback(async (name, email, password, role) => {
    const res = await registerUser(name, email, password, role)
    const data = res.data
    const session = { userId: data.userId, name: data.name, email: data.email, role: data.role }
    setToken(data.accessToken)
    setUser(session)
    localStorage.setItem('lendai_token', data.accessToken)
    localStorage.setItem('lendai_user', JSON.stringify(session))
    return session
  }, [])

  const logout = useCallback(() => {
    setToken('')
    setUser(null)
    localStorage.removeItem('lendai_token')
    localStorage.removeItem('lendai_user')
  }, [])

  return (
    <AuthContext.Provider value={{ token, user, isAuthenticated, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
