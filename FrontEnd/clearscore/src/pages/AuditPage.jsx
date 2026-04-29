import { useEffect, useState } from 'react'
import { getAuditLogs, getAuditDetail, getCsvStats } from '../api'

export default function AuditPage() {
  const [logs, setLogs] = useState([])
  const [csvStats, setCsvStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [expandedId, setExpandedId] = useState(null)
  const [detail, setDetail] = useState(null)
  const [page, setPage] = useState(0)
  const perPage = 10

  useEffect(() => {
    async function load() {
      try {
        const [l, csv] = await Promise.all([
          getAuditLogs().catch(() => ({ data: [] })),
          getCsvStats().catch(() => ({ data: null })),
        ])
        setLogs(l.data || [])
        setCsvStats(csv.data)
      } finally { setLoading(false) }
    }
    load()
  }, [])

  async function toggleExpand(appId) {
    if (expandedId === appId) { setExpandedId(null); setDetail(null); return }
    setExpandedId(appId)
    try { const res = await getAuditDetail(appId); setDetail(res.data) } catch { setDetail(null) }
  }

  const paged = logs.slice(page * perPage, (page + 1) * perPage)
  const totalPages = Math.ceil(logs.length / perPage)
  const fmt = (v) => v == null ? '—' : '₹' + Number(v).toLocaleString()

  // CSV-based stats
  const totalDecisions = csvStats?.total_decisions ?? 0
  const approvalRate = csvStats?.approval_rate ?? '—'
  const avgProb = csvStats?.avg_probability ?? '—'
  const approved = csvStats?.approved ?? 0
  const rejected = csvStats?.rejected ?? 0

  return (
    <div className="page-content">
      <header className="page-header">
        <div>
          <h1>Audit Registry</h1>
          <p className="page-desc">Transparent logging of AI decision-making. Statistics computed from {totalDecisions.toLocaleString()} real CSV records.</p>
        </div>
        <div className="card card-security-badge">
          <span className="material-symbols-outlined icon-emerald">verified_user</span>
          <div>
            <span className="form-label">SECURITY STATUS</span>
            <strong>All decisions are securely logged</strong>
          </div>
        </div>
      </header>

      {/* Stats from CSV */}
      <div className="grid-3 mb-24">
        <div className="card stat-card stat-emerald">
          <span className="form-label">TOTAL RECORDS ANALYZED</span>
          <h2>{totalDecisions.toLocaleString()}</h2>
          <p className="stat-sub text-emerald">{approved.toLocaleString()} approved / {rejected.toLocaleString()} rejected</p>
        </div>
        <div className="card stat-card stat-blue">
          <span className="form-label">AVG. APPROVAL PROBABILITY</span>
          <h2>{typeof avgProb === 'number' ? (avgProb * 100).toFixed(1) + '%' : '—'}</h2>
          <p className="stat-sub muted-text">Historical mean across CSV dataset</p>
        </div>
        <div className="card stat-card stat-slate">
          <span className="form-label">APPROVAL RATE</span>
          <h2>{typeof approvalRate === 'number' ? approvalRate + '%' : '—'}</h2>
          <p className="stat-sub muted-text">Computed from real applicant data</p>
        </div>
      </div>

      <div className="card table-card mb-24">
        <div className="table-scroll">
          <table className="audit-table">
            <thead><tr><th>ID</th><th>Income</th><th>Score</th><th>Result</th><th className="text-right">Time</th></tr></thead>
            <tbody>
              {paged.map((log) => (
                <tr key={log.auditId} className="audit-row" onClick={() => log.applicationId && toggleExpand(log.applicationId)}>
                  <td className="mono">#{log.applicationId ? `LN-${log.applicationId}` : log.auditId}</td>
                  <td>{fmt(log.income)}</td>
                  <td>{log.creditScore ?? '—'}</td>
                  <td>
                    <span className={`status-pill ${log.decision === 'APPROVED' ? 'pill-approved' : log.decision === 'REJECTED' ? 'pill-rejected' : 'pill-review'}`}>
                      {log.decision || 'PENDING'}
                    </span>
                  </td>
                  <td className="text-right muted-text">{log.timestamp ? new Date(log.timestamp).toLocaleString() : '—'}</td>
                </tr>
              ))}
              {paged.length === 0 && !loading && (
                <tr><td colSpan={5} className="empty-table">No audit entries yet. Submit a loan application to generate records.</td></tr>
              )}
            </tbody>
          </table>
        </div>
        <div className="table-footer">
          <span className="form-label">SHOWING {paged.length} OF {logs.length} ENTRIES</span>
          <div className="pagination">
            <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="page-btn"><span className="material-symbols-outlined">chevron_left</span></button>
            <button disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)} className="page-btn"><span className="material-symbols-outlined">chevron_right</span></button>
          </div>
        </div>
      </div>

      {expandedId && detail && (
        <div className="card mb-24">
          <h3 className="card-label">DECISION DETAIL — #{expandedId}</h3>
          <div className="detail-grid">
            <div><span className="form-label">PROBABILITY</span><p>{detail.decision?.probability ? (Number(detail.decision.probability) * 100).toFixed(1) + '%' : '—'}</p></div>
            <div><span className="form-label">MODEL</span><p>{detail.decision?.modelVersion || '—'}</p></div>
            {detail.shapExplanations?.length > 0 && (
              <div className="detail-shap">
                <span className="form-label">TOP SHAP VALUES</span>
                {detail.shapExplanations.slice(0, 5).map((s, i) => (
                  <div key={i} className="shap-row">
                    <span>{s.featureName?.replace(/_/g, ' ')}</span>
                    <span className={Number(s.shapValue) >= 0 ? 'text-emerald' : 'text-red'}>{Number(s.shapValue).toFixed(4)}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {loading && <div className="loading-text">Loading audit data...</div>}
    </div>
  )
}
