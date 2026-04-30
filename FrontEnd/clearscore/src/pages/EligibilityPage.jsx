import { useState, useEffect } from 'react'
import { predictLoan, getCsvProfile } from '../api'
import { useAuth } from '../context/AuthContext'
import { useDecision } from '../context/DecisionContext'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'

export default function EligibilityPage() {
  const { user } = useAuth()
  const { setLatestDecision } = useDecision()
  const [loanAmount, setLoanAmount] = useState(500000)
  const [profile, setProfile] = useState(null)
  const [profileLoading, setProfileLoading] = useState(true)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  // Fetch CSV profile on page load (JWT userId + 1000)
  useEffect(() => {
    getCsvProfile()
      .then(res => setProfile(res.data))
      .catch(() => setProfile(null))
      .finally(() => setProfileLoading(false))
  }, [])

  function estimateSafeMaxLoan(income, loan, probability) {
    const annualIncome = Number(income || 0)
    const requested = Number(loan || 0)
    const confidence = Number(probability || 0)
    if (!annualIncome) return null
    const baseCap = annualIncome * 4
    const multiplier = Math.max(0.7, Math.min(1.1, 0.85 + confidence / 250))
    const estimatedMax = Math.min(baseCap * multiplier, Math.max(requested * 1.35, annualIncome * 1.5))
    return Math.max(0, Math.round(estimatedMax))
  }

  function buildUserContextSnapshot(resultData, currentLoanAmount) {
    const timelineItems = (resultData?.timeline || profile?.timeline || []).slice(0, 4).map((item) => ({
      year: item.year,
      event: item.event,
      detail: item.detail,
    }))
    const riskPath = (resultData?.risk_evolution || profile?.risk_evolution || []).slice(-4).map((item) => ({
      year: item.year,
      riskScore: item.riskScore,
    }))
    const probability = Number(resultData?.probability || 0)
    const currentLoan = Number(currentLoanAmount || 0)
    const annualIncome = Number(profile?.income || 0)

    return {
      userId: profile?.user_id || user?.userId || null,
      profileSummary: {
        income: annualIncome,
        creditAmount: Number(profile?.credit_amount || 0),
        annuity: Number(profile?.annuity || 0),
        age: Number(profile?.age || 0),
        employmentYears: Number(profile?.employment_years || 0),
        familyMembers: Number(profile?.family_members || 0),
      },
      decisionSummary: {
        decision: resultData?.decision || '',
        probability,
        riskScore: Number(resultData?.risk_score || 0),
        riskLabel: resultData?.risk_label || '',
        currentLoanAmount: currentLoan,
        estimatedMaxLoan: estimateSafeMaxLoan(annualIncome, currentLoan, probability),
      },
      timelineSummary: timelineItems,
      riskEvolutionSummary: riskPath,
      recommendation: resultData?.recommendation || '',
      topQuestions: [
        'Why was this decision made?',
        'How can I improve it?',
        'How much more can I borrow?'
      ],
    }
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setLoading(true)
    setError('')
    setResult(null)
    try {
      const res = await predictLoan(loanAmount)
      setResult(res.data)
      const snapshot = buildUserContextSnapshot(res.data, loanAmount)
      setLatestDecision({
        decision: res.data?.decision,
        probability: Number(res.data?.probability || 0),
        reasons: Array.isArray(res.data?.reasons) ? res.data.reasons.filter(Boolean) : [],
        loanAmount: Number(loanAmount || 0),
        annualIncome: Number(profile?.income || 0),
        riskScore: Number(res.data?.risk_score || 0),
        riskLabel: res.data?.risk_label || '',
        recommendation: res.data?.recommendation || '',
        contextSnapshot: snapshot,
      })
    } catch (err) {
      setError(err?.response?.data?.detail || 'Prediction failed. Ensure ML service (port 8000) and Spring Boot (port 8080) are running.')
    } finally {
      setLoading(false)
    }
  }

  const isApproved = result?.decision === 'APPROVED'
  const topFeatures = result?.top_features || []
  const reasons = result?.reasons || []
  const nextSteps = result?.next_steps || []
  const timeline = result?.timeline || profile?.timeline || []
  const riskEvolution = result?.risk_evolution || profile?.risk_evolution || []

  return (
    <div className="page-content">
      <header className="page-header center-header">
        <h1>Loan Eligibility Check</h1>
        <p className="page-desc">
          Your financial profile is automatically loaded from the credit database.
          Simply enter a loan amount to get an instant AI-powered decision.
        </p>
      </header>

      <div className="eligibility-grid">
        {/* Left: Profile + Form */}
        <section className="card form-card">
          {/* Auto-loaded CSV profile */}
          <h2>Your Financial Profile</h2>
          {profileLoading ? (
            <p className="muted-text" style={{padding: '20px 0'}}>Loading your profile...</p>
          ) : profile && !profile.error ? (
            <div className="csv-info" style={{borderTop:'none', paddingTop: 0}}>
              <p className="muted-text" style={{fontSize: 13, marginBottom: 16}}>
                Data fetched from credit database (ID: <strong>{profile.user_id}</strong>)
              </p>
              <div className="csv-fields">
                <div><span className="form-label">ANNUAL INCOME</span><p>₹{Number(profile.income).toLocaleString()}</p></div>
                <div><span className="form-label">CURRENT CREDIT</span><p>₹{Number(profile.credit_amount).toLocaleString()}</p></div>
                <div><span className="form-label">MONTHLY ANNUITY</span><p>₹{Number(profile.annuity).toLocaleString()}</p></div>
                <div><span className="form-label">AGE</span><p>{profile.age} years</p></div>
                <div><span className="form-label">EMPLOYMENT</span><p>{profile.employment_years} years</p></div>
                <div><span className="form-label">FAMILY MEMBERS</span><p>{profile.family_members}</p></div>
              </div>
            </div>
          ) : (
            <p className="muted-text" style={{padding: '20px 0'}}>Could not load profile. Make sure the ML service is running.</p>
          )}

          {/* Loan Amount Input */}
          <div style={{borderTop: '1px solid var(--border)', marginTop: 20, paddingTop: 20}}>
            <h3 style={{color: 'var(--text-heading)', marginBottom: 16}}>Enter Loan Amount</h3>
            <form className="elig-form" onSubmit={handleSubmit}>
              <label className="form-field">
                <span className="form-label">DESIRED LOAN AMOUNT (₹)</span>
                <input
                  type="number"
                  value={loanAmount}
                  onChange={e => setLoanAmount(Number(e.target.value))}
                  placeholder="e.g. 500000"
                  min="1"
                  required
                />
              </label>
              <button className="btn-success full-width" type="submit" disabled={loading}>
                {loading ? 'Analyzing...' : 'Check Loan Eligibility'}
              </button>
            </form>
            {error && <div className="form-error mt-12">{error}</div>}
          </div>
        </section>

        {/* Right: Results */}
        <div className="results-col">
          {/* Decision Card */}
          <div className={`card decision-card ${result ? (isApproved ? 'decision-approved' : 'decision-rejected') : ''}`}>
            {result ? (
              <>
                <div className="decision-icon-wrap">
                  <span className="material-symbols-outlined decision-icon" style={{ fontVariationSettings: "'FILL' 1" }}>
                    {isApproved ? 'check_circle' : 'cancel'}
                  </span>
                </div>
                <h2 className={isApproved ? 'text-emerald' : 'text-red'}>
                  {isApproved ? 'Loan Approved' : 'Loan Rejected'}
                </h2>
                <p className="muted-text">{result.message}</p>
                <div className="decision-metrics">
                  <div><span>Confidence</span><strong>{(Number(result.probability) * 100).toFixed(1)}%</strong></div>
                  <div><span>Risk Score</span><strong>{Number(result.risk_score || 0).toFixed(1)}</strong></div>
                  <div><span>Risk Label</span><strong>{result.risk_label || '—'}</strong></div>
                </div>
                {result.csvMappedId && (
                  <p className="muted-text" style={{marginTop: 12, fontSize: 12}}>
                    CSV Row: #{result.csvMappedId} • Auth User: {result.authUserId}
                  </p>
                )}
              </>
            ) : (
              <div className="decision-placeholder">
                <span className="material-symbols-outlined" style={{ fontSize: 48, color: '#334155' }}>credit_score</span>
                <h2>Awaiting Submission</h2>
                <p className="muted-text">Enter a loan amount and click "Check Loan Eligibility" to see your AI-powered decision.</p>
              </div>
            )}
          </div>

          {/* Explanation Cards */}
          {result && (
            <div className="bento-2">
              <div className="card card-compact">
                <div className="card-head-row">
                  <span className="material-symbols-outlined icon-emerald">lightbulb</span>
                  <h3>Why this result?</h3>
                </div>
                <ul className="reason-list">
                  {reasons.map((r, i) => (
                    <li key={i}>
                      <span className="material-symbols-outlined icon-emerald-sm">check</span>
                      <span>{r}</span>
                    </li>
                  ))}
                </ul>
              </div>
              <div className="card card-compact">
                <div className="card-head-row">
                  <span className="material-symbols-outlined icon-blue">trending_up</span>
                  <h3>Next Steps</h3>
                </div>
                <ul className="reason-list">
                  {nextSteps.map((s, i) => (
                    <li key={i}>
                      <span className="material-symbols-outlined icon-blue-sm">arrow_forward</span>
                      <span>{s}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          )}

          {/* Decision Drivers — SHAP Values */}
          {topFeatures.length > 0 && (
            <div className="card">
              <h3 className="card-label">DECISION DRIVERS (SHAP)</h3>
              <div className="drivers-list">
                {topFeatures.map((feat, i) => (
                  <div key={i} className="driver-row">
                    <div className="driver-name">
                      <span className={`driver-dot ${feat.direction === 'positive' ? 'impact-high' : 'impact-low'}`}></span>
                      <span>{feat.feature.replace(/_/g, ' ')}</span>
                    </div>
                    <div className="driver-impact">
                      <span style={{ fontSize: 12, fontWeight: 700, color: feat.direction === 'positive' ? 'var(--emerald)' : 'var(--red)' }}>
                        {feat.direction === 'positive' ? '↑ Positive' : '↓ Negative'}
                      </span>
                      <span className="mono" style={{ fontSize: 12, color: 'var(--text-muted)' }}>{Number(feat.value).toFixed(4)}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      <div className="grid-2 mt-24">
        <div className="card">
          <h3 className="card-label">CREDIT HISTORY TIMELINE</h3>
          <div className="timeline-list">
            {timeline.slice(0, 8).map((item, index) => (
              <div className="timeline-item" key={`${item.date}-${index}`}>
                <span className="timeline-year">{item.year}</span>
                <div>
                  <strong>{item.event}</strong>
                  <p>{item.detail} {item.amount ? `• ₹${Number(item.amount).toLocaleString()}` : ''}</p>
                </div>
              </div>
            ))}
            {timeline.length === 0 && <p className="muted-text">Timeline appears after your CSV profile loads.</p>}
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
            ) : <div className="empty-chart">Loading risk evolution...</div>}
          </div>
        </div>
      </div>

      <div className="banner-card mt-24">
        <div>
          <h3>Credit History Reconstruction</h3>
          <p>{profile?.data_note || 'We reconstruct applicant history using related datasets and simulate decision tracking over time.'}</p>
        </div>
      </div>
    </div>
  )
}
