import { useEffect, useState } from 'react'
import { getLoanHistory } from '../api'
import { useAuth } from '../context/AuthContext'

export default function ProfilePage() {
  const { user } = useAuth()
  const [history, setHistory] = useState([])

  useEffect(() => {
    getLoanHistory().then(r => setHistory(r.data || [])).catch(() => {})
  }, [])

  return (
    <div className="page-content">
      <header className="page-header">
        <h1>Profile</h1>
        <p className="page-desc">Your account details and prediction history.</p>
      </header>

      <div className="grid-2 mb-24">
        <div className="card">
          <h3 className="card-label">ACCOUNT INFORMATION</h3>
          <div className="profile-info">
            <div className="profile-avatar">{(user?.name || 'U')[0].toUpperCase()}</div>
            <div className="profile-fields">
              <div><span className="form-label">NAME</span><p>{user?.name || '—'}</p></div>
              <div><span className="form-label">EMAIL</span><p>{user?.email || '—'}</p></div>
              <div><span className="form-label">ROLE</span><p>{user?.role || 'USER'}</p></div>
              <div><span className="form-label">USER ID</span><p className="mono">{user?.userId || '—'}</p></div>
            </div>
          </div>
        </div>
        <div className="card">
          <h3 className="card-label">QUICK STATS</h3>
          <div className="profile-stats">
            <div className="kpi-card"><span className="kpi-label">Total Checks</span><span className="kpi-value">{history.length}</span></div>
            <div className="kpi-card"><span className="kpi-label">Approved</span><span className="kpi-value text-emerald">{history.filter(h => h.decision === 'APPROVED').length}</span></div>
            <div className="kpi-card"><span className="kpi-label">Rejected</span><span className="kpi-value text-red">{history.filter(h => h.decision !== 'APPROVED').length}</span></div>
          </div>
        </div>
      </div>

      <div className="card">
        <h3 className="card-label">PREDICTION HISTORY</h3>
        {history.length > 0 ? (
          <div className="table-scroll">
            <table className="audit-table">
              <thead><tr><th>ID</th><th>Decision</th><th>Probability</th><th>Loan Amount</th><th>Income</th><th className="text-right">Date</th></tr></thead>
              <tbody>
                {history.map(h => (
                  <tr key={h.applicationId}>
                    <td className="mono">#{h.applicationId}</td>
                    <td><span className={`status-pill ${h.decision === 'APPROVED' ? 'pill-approved' : 'pill-rejected'}`}>{h.decision}</span></td>
                    <td>{h.probability ? (Number(h.probability) * 100).toFixed(1) + '%' : '—'}</td>
                    <td>${Number(h.loanAmount || 0).toLocaleString()}</td>
                    <td>${Number(h.income || 0).toLocaleString()}</td>
                    <td className="text-right muted-text">{h.appliedAt ? new Date(h.appliedAt).toLocaleString() : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="muted-text" style={{ padding: '2rem' }}>No prediction history yet. Submit a loan application from the Eligibility page.</p>
        )}
      </div>
    </div>
  )
}
