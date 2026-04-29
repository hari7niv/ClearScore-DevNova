import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await login(email, password)
      navigate('/dashboard')
    } catch (err) {
      setError(err?.response?.data?.detail || 'Invalid email or password')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-header">
            <div className="auth-logo">L</div>
            <h1>Welcome to LendAI</h1>
            <p>Sign in to access the credit decisioning platform</p>
          </div>

          <form className="auth-form" onSubmit={handleSubmit}>
            <label className="form-field">
              <span className="form-label">EMAIL</span>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                required
              />
            </label>
            <label className="form-field">
              <span className="form-label">PASSWORD</span>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                required
              />
            </label>
            {error && <div className="form-error">{error}</div>}
            <button className="btn-primary" type="submit" disabled={loading}>
              {loading ? 'Signing in...' : 'Sign In'}
            </button>
          </form>

          <p className="auth-switch">
            Don&apos;t have an account? <Link to="/register">Create one</Link>
          </p>
        </div>

        <div className="auth-features">
          <h3>Transparent AI Lending</h3>
          <ul>
            <li>
              <span className="material-symbols-outlined feat-icon">verified</span>
              <span>Explainable ML predictions with SHAP values</span>
            </li>
            <li>
              <span className="material-symbols-outlined feat-icon">shield</span>
              <span>Tamper-proof audit trail with hash chaining</span>
            </li>
            <li>
              <span className="material-symbols-outlined feat-icon">balance</span>
              <span>Real-time fairness monitoring &amp; compliance</span>
            </li>
            <li>
              <span className="material-symbols-outlined feat-icon">lock</span>
              <span>JWT-protected, role-based access control</span>
            </li>
          </ul>
        </div>
      </div>
    </div>
  )
}
