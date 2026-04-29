import { useEffect, useState } from 'react'
import { getCsvProfile, getLoanHistory } from '../api'
import { useAuth } from '../context/AuthContext'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'

export default function ProfilePage() {
  const { user } = useAuth()
  const [history, setHistory] = useState([])
  const [profile, setProfile] = useState(null)

  useEffect(() => {
    getLoanHistory().then(r => setHistory(r.data || [])).catch(() => {})
    getCsvProfile().then(r => setProfile(r.data)).catch(() => {})
  }, [])

  const timeline = profile?.timeline || []
  const riskEvolution = profile?.risk_evolution || []

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
            <div className="kpi-card"><span className="kpi-label">Income</span><span className="kpi-value">₹{Number(profile?.income || 0).toLocaleString()}</span></div>
            <div className="kpi-card"><span className="kpi-label">Loan History</span><span className="kpi-value">{profile?.loan_history_count ?? history.length}</span></div>
            <div className="kpi-card"><span className="kpi-label">Current Risk</span><span className="kpi-value">{profile?.current_risk || '—'}</span></div>
          </div>
          <p className="muted-text mt-16">Defaults: {profile?.defaults ?? 0} • {profile?.data_note || 'History loads from the related credit datasets.'}</p>
        </div>
      </div>

      <div className="grid-2 mb-24">
        <div className="card">
          <h3 className="card-label">CREDIT HISTORY TIMELINE</h3>
          <div className="timeline-list">
            {timeline.map((item, index) => (
              <div className="timeline-item" key={`${item.date}-${index}`}>
                <span className="timeline-year">{item.year}</span>
                <div>
                  <strong>{item.event}</strong>
                  <p>{item.detail} {item.amount ? `• ₹${Number(item.amount).toLocaleString()}` : ''}</p>
                </div>
              </div>
            ))}
            {timeline.length === 0 && <p className="muted-text">No reconstructed credit events yet.</p>}
          </div>
        </div>
        <div className="card">
          <h3 className="card-label">RISK EVOLUTION</h3>
          <div className="chart-wrapper">
            {riskEvolution.length > 0 ? (
              <ResponsiveContainer width="100%" height={280}>
                <LineChart data={riskEvolution}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                  <XAxis dataKey="year" stroke="#94a3b8" fontSize={12} />
                  <YAxis stroke="#94a3b8" fontSize={12} domain={[0, 100]} />
                  <Tooltip contentStyle={{ background: '#1e293b', border: '1px solid #334155', borderRadius: 8, color: '#e2e8f0' }} />
                  <Line type="monotone" dataKey="riskScore" stroke="#fbbf24" strokeWidth={3} name="Risk score" />
                </LineChart>
              </ResponsiveContainer>
            ) : <div className="empty-chart">Loading risk timeline...</div>}
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
                  <tr key={h.applicationId || h.auditId}>
                    <td className="mono">#{h.applicationId || `AUD-${h.auditId}`}</td>
                    <td><span className={`status-pill ${h.decision === 'APPROVED' ? 'pill-approved' : 'pill-rejected'}`}>{h.decision}</span></td>
                    <td>{h.probability ? (Number(h.probability) * 100).toFixed(1) + '%' : '—'}</td>
                    <td>₹{Number(h.loanAmount || 0).toLocaleString()}</td>
                    <td>{h.income ? `₹${Number(h.income).toLocaleString()}` : '—'}</td>
                    <td className="text-right muted-text">{(h.appliedAt || h.timestamp) ? new Date(h.appliedAt || h.timestamp).toLocaleString() : '—'}</td>
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
