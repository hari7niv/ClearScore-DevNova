import { useEffect, useState } from 'react'
import { getCsvStats, getAuditStats, getCsvProfile } from '../api'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, LineChart, Line } from 'recharts'

export default function DashboardPage() {
  const [csvStats, setCsvStats] = useState(null)
  const [profile, setProfile] = useState(null)
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [showInsights, setShowInsights] = useState(false)

  useEffect(() => {
    async function load() {
      try {
        const [csv, s, csvProfile] = await Promise.all([
          getCsvStats().catch(() => ({ data: null })),
          getAuditStats().catch(() => ({ data: null })),
          getCsvProfile().catch(() => ({ data: null })),
        ])
        setCsvStats(csv.data)
        setStats(s.data)
        setProfile(csvProfile.data)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  const approvalRate = typeof csvStats?.approval_rate === 'number' ? csvStats.approval_rate : null
  const approved = csvStats?.approved ?? 0
  const rejected = csvStats?.rejected ?? 0
  const totalDecisions = csvStats?.total_decisions ?? null
  const adverseImpact = csvStats?.adverse_impact_ratio ?? null
  const modelDrift = csvStats?.model_drift_psi ?? null
  const currentRiskScore = typeof profile?.current_risk_score === 'number' ? profile.current_risk_score : null
  const currentRiskLabel = profile?.current_risk || '—'
  const estimatedCreditScore = currentRiskScore !== null ? Math.max(300, Math.min(850, Math.round(850 - (currentRiskScore * 5.5)))) : null

  const probabilityData = csvStats?.probability_distribution || []
  const decisionsOverTime = csvStats?.decisions_over_time || []
  const incomeData = (csvStats?.fairness_by_income || []).map(item => ({ segment: item.group, approvalRate: item.approval_rate, total: item.total, approved: item.approved }))

  // Derive simple human-readable reasons (fallbacks when per-applicant data not provided)
  const keyReasons = []
  if (csvStats?.avg_loan_to_income_ratio && csvStats.avg_loan_to_income_ratio > 0.6) keyReasons.push('Loan amount high relative to income')
  if (modelDrift && modelDrift > 0.2) keyReasons.push('Model behavior has shifted recently')
  if (keyReasons.length === 0) keyReasons.push('Credit history and income are primary drivers')

  const decisionText = approvalRate !== null ? (approvalRate >= 50 ? 'Approved' : 'Review / Potentially Rejected') : 'Decision summary'
  const decisionClass = approvalRate !== null ? (approvalRate >= 50 ? 'decision-approved' : 'decision-rejected') : ''
  const fairnessStatus = adverseImpact !== null && adverseImpact >= 0.8 ? 'Fairness check: clear' : 'Fairness check: review'
  const fairnessCopy = adverseImpact !== null
    ? `Outcomes are compared across applicant groups. Current adverse impact ratio is ${adverseImpact.toFixed(2)}, with 0.80 used as the internal review threshold.`
    : 'Fairness signals are not available yet.'

  return (
    <div className="page-content">
      <header className="page-header">
        <div>
          <h1>Credit Decision Dashboard</h1>
          <p className="page-desc">Clear, concise decision summaries and trust signals for lending.</p>
        </div>
      </header>

      <div className="grid-2-1 mb-24">
        <div className={`card decision-card animate-card ${decisionClass}`} style={{ '--delay': '0ms' }}>
          <div className="decision-icon-wrap">
            <div className="decision-icon">
              {approvalRate !== null ? (
                approvalRate >= 50 ? <span className="material-symbols-outlined" aria-hidden>check_circle</span> : <span className="material-symbols-outlined" aria-hidden>warning</span>
              ) : (
                <span className="material-symbols-outlined" aria-hidden>info</span>
              )}
            </div>
          </div>
          <h2>{decisionText}</h2>
          <p className="muted-text">Probability: {approvalRate !== null ? `${Math.round(approvalRate)}%` : '—'}</p>

          <div className="bento-2 mt-16">
            <div>
              <h4 className="card-label">Key reasons</h4>
              <ul className="reason-list">
                {keyReasons.slice(0,3).map((r,i) => (
                  <li key={i}><span className="reason-icon material-symbols-outlined" aria-hidden>insights</span><span><strong>{i+1}.</strong>&nbsp; {r}</span></li>
                ))}
              </ul>
            </div>

            <div>
              <h4 className="card-label">Metrics</h4>
              <div className="decision-metrics" style={{marginTop:8}}>
                <div>
                  <span>Approval Rate</span>
                  <strong>{approvalRate !== null ? `${Math.round(approvalRate)}%` : '—'}</strong>
                </div>
                <div>
                  <span>Total Decisions</span>
                  <strong>{totalDecisions !== null ? Number(totalDecisions).toLocaleString() : '—'}</strong>
                </div>
                <div>
                  <span>Approved / Rejected</span>
                  <strong>{approved} / {rejected}</strong>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="card card-compact animate-card" style={{ '--delay': '90ms' }}>
          <h3 className="card-label">Trust &amp; Fairness</h3>
          <div style={{display:'flex', flexDirection:'column', gap:12, marginTop:12}}>
            <div>
              <strong style={{color: adverseImpact !== null && adverseImpact >= 0.8 ? 'var(--emerald)' : 'var(--amber)'}}>{fairnessStatus}</strong>
              <div className="muted-text" style={{marginTop:6}}>
                {fairnessCopy}
              </div>
            </div>

            <div>
              <strong>One-line explanation</strong>
              <div className="muted-text" style={{marginTop:6}}>The system compares loan amount, repayment history, and profile stability to produce a concise outcome.</div>
            </div>

            <div style={{marginTop:12}}>
              <button className="btn-primary" onClick={() => setShowInsights(s => !s)}>{showInsights ? 'Hide insights' : 'Show insights'}</button>
            </div>
          </div>
        </div>
      </div>

      <div className="grid-4 mb-24">
        <div className="kpi-card animate-card" style={{ '--delay': '90ms' }}>
          <span className="kpi-label">Credit Score</span>
          <span className="kpi-value">{estimatedCreditScore !== null ? estimatedCreditScore : '—'}</span>
          <span className="kpi-status muted-text">Estimated from the current risk profile</span>
        </div>
        <div className="kpi-card animate-card" style={{ '--delay': '150ms' }}>
          <span className="kpi-label">Risk Level</span>
          <span className="kpi-value">{currentRiskLabel}</span>
          <span className="kpi-status muted-text">{currentRiskScore !== null ? `Risk score ${currentRiskScore.toFixed(1)}/100` : 'Profile not loaded yet'}</span>
        </div>
        <div className="kpi-card animate-card" style={{ '--delay': '210ms' }}>
          <span className="kpi-label">Income</span>
          <span className="kpi-value">{profile?.income ? `₹${Number(profile.income).toLocaleString()}` : '—'}</span>
          <span className="kpi-status muted-text">Pulled from reconstructed profile data</span>
        </div>
        <div className="kpi-card animate-card" style={{ '--delay': '270ms' }}>
          <span className="kpi-label">Loan Amount</span>
          <span className="kpi-value">{profile?.credit_amount ? `₹${Number(profile.credit_amount).toLocaleString()}` : '—'}</span>
          <span className="kpi-status muted-text">Current benchmark amount in the profile</span>
        </div>
      </div>

      <div className="grid-3 mb-24">
        <div className="kpi-card animate-card" style={{ '--delay': '120ms' }}>
          <span className="kpi-label">Approval Rate</span>
          <span className="kpi-value">{approvalRate !== null ? `${Math.round(approvalRate)}%` : '—'}</span>
          <span className="kpi-status muted-text">Snapshot across recent decisions</span>
        </div>
        <div className="kpi-card animate-card" style={{ '--delay': '180ms' }}>
          <span className="kpi-label">Model Drift</span>
          <span className="kpi-value">{modelDrift !== null ? modelDrift.toFixed(3) : '—'}</span>
          <span className="kpi-status muted-text">Low values indicate stability</span>
        </div>
        <div className="kpi-card animate-card" style={{ '--delay': '240ms' }}>
          <span className="kpi-label">Audit Confidence</span>
          <span className="kpi-value">{csvStats?.audit_confidence ? `${csvStats.audit_confidence}%` : '—'}</span>
          <span className="kpi-status muted-text">Automated checks</span>
        </div>
      </div>

      {showInsights && (
        <div className="card mb-24 animate-card insights-panel" style={{ '--delay': '0ms' }}>
          <h3 className="card-label">Insights &amp; Charts</h3>
          <div className="grid-2 mt-12">
            <div className="chart-shell" style={{ '--delay': '80ms' }}>
              <h4 className="card-label">Approval Rate by Income</h4>
              {incomeData.length > 0 ? (
                <ResponsiveContainer width="100%" height={220}>
                  <BarChart data={incomeData} barCategoryGap={18}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                    <XAxis dataKey="segment" stroke="#94a3b8" fontSize={12} />
                    <YAxis stroke="#94a3b8" fontSize={12} />
                    <Tooltip animationDuration={220} />
                    <Bar dataKey="approvalRate" fill="#10b981" radius={[6,6,0,0]} isAnimationActive animationDuration={900} />
                  </BarChart>
                </ResponsiveContainer>
              ) : (
                <div className="empty-chart">No income distribution data</div>
              )}
            </div>

            <div className="chart-shell" style={{ '--delay': '160ms' }}>
              <h4 className="card-label">Decisions Over Time</h4>
              {decisionsOverTime.length > 0 ? (
                <ResponsiveContainer width="100%" height={220}>
                  <LineChart data={decisionsOverTime}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                    <XAxis dataKey="date" stroke="#94a3b8" fontSize={12} />
                    <YAxis stroke="#94a3b8" fontSize={12} />
                    <Tooltip animationDuration={220} />
                    <Line type="monotone" dataKey="approved" stroke="#10b981" strokeWidth={3} dot={false} isAnimationActive animationDuration={900} />
                    <Line type="monotone" dataKey="rejected" stroke="#f87171" strokeWidth={3} dot={false} isAnimationActive animationDuration={900} />
                  </LineChart>
                </ResponsiveContainer>
              ) : (
                <div className="empty-chart">No trend data</div>
              )}
            </div>
          </div>
        </div>
      )}

      {loading && <div className="loading-text">Loading data…</div>}
    </div>
  )
}
