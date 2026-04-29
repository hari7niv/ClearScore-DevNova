import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function RegisterPage() {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState('USER')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const { register } = useAuth()
  const navigate = useNavigate()

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await register(name, email, password, role)
      navigate('/dashboard')
    } catch (err) {
      setError(err?.response?.data?.detail || 'Registration failed')
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
            <h1>Create Account</h1>
            <p>Join LendAI to start using AI-powered credit decisions</p>
          </div>

          <form className="auth-form" onSubmit={handleSubmit}>
            <label className="form-field">
              <span className="form-label">FULL NAME</span>
              <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Asha Kumar" required />
            </label>
            <label className="form-field">
              <span className="form-label">EMAIL</span>
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" required />
            </label>
            <label className="form-field">
              <span className="form-label">PASSWORD</span>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" required />
            </label>
            <label className="form-field">
              <span className="form-label">ROLE</span>
              <select value={role} onChange={(e) => setRole(e.target.value)}>
                <option value="USER">User</option>
                <option value="ADMIN">Admin / Compliance</option>
              </select>
            </label>
            {error && <div className="form-error">{error}</div>}
            <button className="btn-primary" type="submit" disabled={loading}>
              {loading ? 'Creating...' : 'Create Account'}
            </button>
          </form>

          <p className="auth-switch">
            Already have an account? <Link to="/login">Sign in</Link>
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
