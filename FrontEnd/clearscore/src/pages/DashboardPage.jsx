import { useEffect, useState } from 'react'
import { getCsvStats, getAuditStats } from '../api'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, LineChart, Line } from 'recharts'

export default function DashboardPage() {
  const [csvStats, setCsvStats] = useState(null)
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function load() {
      try {
        const [csv, s] = await Promise.all([
          getCsvStats().catch(() => ({ data: null })),
          getAuditStats().catch(() => ({ data: null })),
        ])
        setCsvStats(csv.data)
        setStats(s.data)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  const adverseImpact = csvStats?.adverse_impact_ratio ?? '—'
  const modelDrift = csvStats?.model_drift_psi ?? '—'
  const auditConf = csvStats?.audit_confidence ?? '—'
  const totalDecisions = csvStats?.total_decisions ?? '—'
  const approvalRate = csvStats?.approval_rate ?? '—'
  const approved = csvStats?.approved ?? 0
  const rejected = csvStats?.rejected ?? 0
  const threshold = csvStats?.threshold ?? '—'

  const probabilityData = csvStats?.probability_distribution || []
  const decisionsOverTime = csvStats?.decisions_over_time || []
  const genderData = csvStats?.fairness_by_gender || []
  const datasets = csvStats?.datasets || {}

  // Income fairness data for chart
  const incomeData = (csvStats?.fairness_by_income || []).map(item => ({
    segment: item.group,
    approvalRate: item.approval_rate,
    total: item.total,
    approved: item.approved,
  }))

  return (
    <div className="page-content">
      <header className="page-header">
        <div>
          <span className="page-eyebrow">ETHICS &amp; TRANSPARENCY</span>
          <h1>AI Fairness Protocol</h1>
          <p className="page-desc">
            Fairness metrics computed from {totalDecisions !== '—' ? totalDecisions.toLocaleString() : '...'} real applicant records in the credit database.
            Our AI is audited continuously for neutral decision-making.
          </p>
        </div>
      </header>

      <div className="card mb-24">
        <h3 className="card-label">Dataset Connection</h3>
        <div className="dataset-grid">
          {['bureau.csv', 'bureau_balance.csv', 'previous_application.csv', 'installments_payments.csv'].map((name) => (
            <div className="dataset-item" key={name}>
              <span className={`status-pill ${datasets[name]?.found ? 'pill-approved' : 'pill-review'}`}>
                {datasets[name]?.found ? 'Loaded' : 'Missing'}
              </span>
              <strong>{name}</strong>
              <p>{Number(datasets[name]?.rows_loaded || 0).toLocaleString()} rows</p>
            </div>
          ))}
        </div>
      </div>

      {/* Trust Assessment + Data Impartiality */}
      <div className="grid-2-1 mb-24">
        <div className="card card-trust">
          <div className="card-head-row">
            <span className="material-symbols-outlined icon-emerald">verified_user</span>
            <h2>Trust Assessment</h2>
          </div>
          <div className="trust-checks">
            <div className="trust-item">
              <span className="material-symbols-outlined icon-emerald-fill">check_circle</span>
              <div>
                <strong>{adverseImpact !== '—' && adverseImpact >= 0.8 ? 'No major bias detected' : 'Potential bias detected'}</strong>
                <p>Adverse Impact Ratio: {typeof adverseImpact === 'number' ? adverseImpact.toFixed(3) : '—'} (≥ 0.80 is considered fair).</p>
              </div>
            </div>
            <div className="trust-item">
              <span className="material-symbols-outlined icon-emerald-fill">check_circle</span>
              <div>
                <strong>Fair approval across income groups</strong>
                <p>Overall approval rate: {typeof approvalRate === 'number' ? approvalRate + '%' : '—'} across {totalDecisions} sampled records.</p>
              </div>
            </div>
          </div>
          <div className="trust-footer">
            <span className="trust-audit-time">Threshold: {typeof threshold === 'number' ? threshold.toFixed(2) : '—'}</span>
            <span className="trust-live">
              <span className="pulse-dot"></span>
              System Live
            </span>
          </div>
        </div>

        <div className="card card-impartiality">
          <h3 className="card-label">Decision Summary</h3>
          <div className="impartiality-bars">
            <div className="bar-track">
              <div className="bar-fill" style={{ width: `${typeof approvalRate === 'number' ? approvalRate : 0}%` }}></div>
            </div>
          </div>
          <p className="muted-text mt-16">
            <strong style={{color:'var(--emerald)'}}>{approved}</strong> Approved &nbsp;•&nbsp;
            <strong style={{color:'var(--red)'}}>{rejected}</strong> Rejected &nbsp;•&nbsp;
            <strong>{typeof approvalRate === 'number' ? approvalRate + '%' : '—'}</strong> Approval Rate
          </p>
        </div>
      </div>

      {/* Metric Cards */}
      <div className="grid-3 mb-24">
        <div className="card card-info">
          <span className="material-symbols-outlined icon-emerald">shield_person</span>
          <h4>Human Centric</h4>
          <p>Decisions are cross-checked by expert underwriters if the AI flags an edge case.</p>
        </div>
        <div className="card card-info">
          <span className="material-symbols-outlined icon-emerald">history_edu</span>
          <h4>Decision Logs</h4>
          <p>Every calculation is stored in an immutable ledger for your privacy and review.</p>
        </div>
        <div className="card card-info">
          <span className="material-symbols-outlined icon-emerald">gavel</span>
          <h4>Global Compliance</h4>
          <p>Aligning with GDPR and CCPA financial standards for algorithmic accountability.</p>
        </div>
      </div>

      <div className="grid-3 mb-24">
        {genderData.map((item) => (
          <div className="kpi-card" key={item.group}>
            <span className="kpi-label">{item.group} Approval Rate</span>
            <span className="kpi-value">{Number(item.approval_rate || 0).toFixed(1)}%</span>
            <span className="kpi-status">{item.approved} approved / {item.total} applicants</span>
          </div>
        ))}
        <div className="kpi-card">
          <span className="kpi-label">Demo Positioning</span>
          <span className="kpi-status">{csvStats?.data_note || 'We reconstruct applicant history using related datasets and simulate decision tracking over time.'}</span>
        </div>
      </div>

      {/* KPI Strip */}
      <div className="grid-4 mb-24">
        <div className="kpi-card">
          <span className="kpi-label">Adverse Impact Ratio</span>
          <span className="kpi-value">{typeof adverseImpact === 'number' ? adverseImpact.toFixed(3) : adverseImpact}</span>
        </div>
        <div className="kpi-card">
          <span className="kpi-label">Model Drift (PSI)</span>
          <span className="kpi-value">{typeof modelDrift === 'number' ? modelDrift.toFixed(4) : modelDrift}</span>
        </div>
        <div className="kpi-card">
          <span className="kpi-label">Audit Confidence</span>
          <span className="kpi-value">{typeof auditConf === 'number' ? auditConf.toFixed(1) + '%' : auditConf}</span>
        </div>
        <div className="kpi-card">
          <span className="kpi-label">Total Decisions</span>
          <span className="kpi-value">{typeof totalDecisions === 'number' ? totalDecisions.toLocaleString() : totalDecisions}</span>
        </div>
      </div>

      {/* Charts */}
      <div className="grid-2 mb-24">
        <div className="card">
          <h3 className="card-label">Approval Rate by Income Bracket</h3>
          <div className="chart-wrapper">
            {incomeData.length > 0 ? (
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={incomeData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                  <XAxis dataKey="segment" stroke="#94a3b8" fontSize={12} />
                  <YAxis stroke="#94a3b8" fontSize={12} domain={[0, 100]} />
                  <Tooltip contentStyle={{ background: '#1e293b', border: '1px solid #334155', borderRadius: 8, color: '#e2e8f0' }} />
                  <Bar dataKey="approvalRate" fill="#10b981" radius={[6, 6, 0, 0]} name="Approval %" />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div className="empty-chart">Loading income distribution data from CSV...</div>
            )}
          </div>
        </div>
        <div className="card">
          <h3 className="card-label">Applications per Income Bracket</h3>
          <div className="chart-wrapper">
            {incomeData.length > 0 ? (
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={incomeData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                  <XAxis dataKey="segment" stroke="#94a3b8" fontSize={12} />
                  <YAxis stroke="#94a3b8" fontSize={12} />
                  <Tooltip contentStyle={{ background: '#1e293b', border: '1px solid #334155', borderRadius: 8, color: '#e2e8f0' }} />
                  <Bar dataKey="total" fill="#60a5fa" radius={[6, 6, 0, 0]} name="Total Applications" />
                  <Bar dataKey="approved" fill="#10b981" radius={[6, 6, 0, 0]} name="Approved" />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div className="empty-chart">Loading application data from CSV...</div>
            )}
          </div>
        </div>
      </div>

      <div className="grid-2 mb-24">
        <div className="card">
          <h3 className="card-label">Probability Distribution</h3>
          <div className="chart-wrapper">
            {probabilityData.length > 0 ? (
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={probabilityData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                  <XAxis dataKey="bucket" stroke="#94a3b8" fontSize={12} />
                  <YAxis stroke="#94a3b8" fontSize={12} />
                  <Tooltip contentStyle={{ background: '#1e293b', border: '1px solid #334155', borderRadius: 8, color: '#e2e8f0' }} />
                  <Bar dataKey="count" fill="#fbbf24" radius={[6, 6, 0, 0]} name="Decisions" />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div className="empty-chart">Loading probability distribution...</div>
            )}
          </div>
        </div>
        <div className="card">
          <h3 className="card-label">Decisions Over Time</h3>
          <div className="chart-wrapper">
            {decisionsOverTime.length > 0 ? (
              <ResponsiveContainer width="100%" height={260}>
                <LineChart data={decisionsOverTime}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                  <XAxis dataKey="date" stroke="#94a3b8" fontSize={12} />
                  <YAxis stroke="#94a3b8" fontSize={12} />
                  <Tooltip contentStyle={{ background: '#1e293b', border: '1px solid #334155', borderRadius: 8, color: '#e2e8f0' }} />
                  <Line type="monotone" dataKey="approved" stroke="#10b981" strokeWidth={3} name="Approved" />
                  <Line type="monotone" dataKey="rejected" stroke="#f87171" strokeWidth={3} name="Rejected" />
                </LineChart>
              </ResponsiveContainer>
            ) : (
              <div className="empty-chart">Loading decision trend...</div>
            )}
          </div>
        </div>
      </div>

      {loading && <div className="loading-text">Loading fairness data from CSV...</div>}
    </div>
  )
}
