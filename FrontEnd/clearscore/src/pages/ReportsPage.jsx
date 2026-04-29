import { useEffect, useState } from 'react'
import { getCsvStats } from '../api'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'

export default function ReportsPage() {
  const [csvStats, setCsvStats] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getCsvStats()
      .then(r => setCsvStats(r.data))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const air = csvStats?.adverse_impact_ratio
  const dii = csvStats?.adverse_impact_ratio  // same metric, different label
  const approvalRate = csvStats?.approval_rate
  const totalDecisions = csvStats?.total_decisions
  const approved = csvStats?.approved ?? 0
  const rejected = csvStats?.rejected ?? 0

  // Income chart data from CSV
  const incomeData = (csvStats?.fairness_by_income || []).map(item => ({
    segment: item.group,
    approvalRate: item.approval_rate,
    total: item.total,
    approved: item.approved,
  }))

  // Cross-segment table
  const crossSegment = (csvStats?.fairness_by_income || []).map(item => ({
    segment: item.group,
    approvalRate: item.approval_rate,
    total: item.total,
    approved: item.approved,
    rejected: item.total - item.approved,
    status: item.approval_rate >= 50 ? 'COMPLIANT' : 'REVIEW',
  }))

  return (
    <div className="page-content">
      <header className="page-header">
        <div>
          <span className="page-eyebrow">COMPLIANCE REPORTS</span>
          <h1>Fairness &amp; Bias Analysis</h1>
          <p className="page-desc">
            Comprehensive fairness metrics computed from {totalDecisions?.toLocaleString() || '...'} real applicant records in the credit database.
          </p>
        </div>
      </header>

      <div className="grid-2 mb-24">
        <div className="kpi-card">
          <span className="kpi-label">Adverse Impact Ratio</span>
          <span className="kpi-value">{air != null ? Number(air).toFixed(3) : '—'}</span>
          <span className={`kpi-status ${air >= 0.8 ? 'kpi-pass' : 'kpi-fail'}`}>{air >= 0.8 ? '✓ Passes 4/5ths Rule' : air != null ? '✗ Below Threshold' : 'Loading...'}</span>
        </div>
        <div className="kpi-card">
          <span className="kpi-label">Overall Approval Rate</span>
          <span className="kpi-value">{approvalRate != null ? approvalRate + '%' : '—'}</span>
          <span className={`kpi-status ${approvalRate >= 40 ? 'kpi-pass' : 'kpi-fail'}`}>
            {approved} approved / {rejected} rejected
          </span>
        </div>
      </div>

      <div className="grid-2 mb-24">
        <div className="card">
          <h3 className="card-label">APPROVAL RATE BY INCOME BRACKET</h3>
          <div className="chart-wrapper">
            {incomeData.length > 0 ? (
              <ResponsiveContainer width="100%" height={280}>
                <BarChart data={incomeData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                  <XAxis dataKey="segment" stroke="#94a3b8" fontSize={12} />
                  <YAxis stroke="#94a3b8" fontSize={12} unit="%" domain={[0, 100]} />
                  <Tooltip contentStyle={{ background: '#1e293b', border: '1px solid #334155', borderRadius: 8, color: '#e2e8f0' }} />
                  <Bar dataKey="approvalRate" fill="#10b981" radius={[6,6,0,0]} name="Approval %" />
                </BarChart>
              </ResponsiveContainer>
            ) : <div className="empty-chart">Loading income data from CSV...</div>}
          </div>
        </div>
        <div className="card">
          <h3 className="card-label">APPLICATIONS BY INCOME BRACKET</h3>
          <div className="chart-wrapper">
            {incomeData.length > 0 ? (
              <ResponsiveContainer width="100%" height={280}>
                <BarChart data={incomeData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
                  <XAxis dataKey="segment" stroke="#94a3b8" fontSize={12} />
                  <YAxis stroke="#94a3b8" fontSize={12} />
                  <Tooltip contentStyle={{ background: '#1e293b', border: '1px solid #334155', borderRadius: 8, color: '#e2e8f0' }} />
                  <Bar dataKey="total" fill="#60a5fa" radius={[6,6,0,0]} name="Total" />
                  <Bar dataKey="approved" fill="#10b981" radius={[6,6,0,0]} name="Approved" />
                </BarChart>
              </ResponsiveContainer>
            ) : <div className="empty-chart">Loading application data from CSV...</div>}
          </div>
        </div>
      </div>

      {crossSegment.length > 0 && (
        <div className="card mb-24">
          <h3 className="card-label">CROSS-SEGMENT ANALYSIS</h3>
          <div className="table-scroll">
            <table className="audit-table">
              <thead><tr><th>Income Group</th><th>Total</th><th>Approved</th><th>Rejected</th><th>Approval %</th><th>Status</th></tr></thead>
              <tbody>
                {crossSegment.map((row, i) => (
                  <tr key={i}>
                    <td>{row.segment}</td>
                    <td>{row.total.toLocaleString()}</td>
                    <td style={{color:'var(--emerald)'}}>{row.approved.toLocaleString()}</td>
                    <td style={{color:'var(--red)'}}>{row.rejected.toLocaleString()}</td>
                    <td>{Number(row.approvalRate).toFixed(1)}%</td>
                    <td><span className={`status-pill ${row.status === 'COMPLIANT' ? 'pill-approved' : 'pill-review'}`}>{row.status}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {loading && <div className="loading-text">Loading fairness report from CSV...</div>}
    </div>
  )
}
