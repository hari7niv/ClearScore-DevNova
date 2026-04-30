import { useMemo, useState } from 'react'
import { getAiExplanation } from '../api'
import { useDecision } from '../context/DecisionContext'

export default function AIAssistantWidget() {
  const { latestDecision } = useDecision()
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [messages, setMessages] = useState([])
  const [question, setQuestion] = useState('')
  const [error, setError] = useState('')

  const canExplain = useMemo(() => {
    return !!latestDecision?.decision && Array.isArray(latestDecision?.reasons) && latestDecision.reasons.length > 0
  }, [latestDecision])

  async function askAssistant(followUpQuestion = '') {
    if (!canExplain) {
      setError('Run an eligibility check first so I can explain your latest decision.')
      return
    }

    setLoading(true)
    setError('')
    try {
      if (followUpQuestion) {
        setMessages(prev => [...prev, { role: 'user', text: followUpQuestion }])
      }

      const res = await getAiExplanation({
        decision: latestDecision.decision,
        probability: latestDecision.probability,
        reasons: latestDecision.reasons,
        question: followUpQuestion || undefined,
        history: messages.slice(-8),
        loan_amount: latestDecision.loanAmount,
        annual_income: latestDecision.annualIncome,
        risk_score: latestDecision.riskScore,
        risk_label: latestDecision.riskLabel,
        user_context: latestDecision.contextSnapshot,
      })

      const reply = res?.data?.explanation || 'No explanation available right now.'
      setMessages(prev => [...prev, { role: 'assistant', text: reply }])
      setQuestion('')
    } catch (err) {
      const detail = err?.response?.data?.detail
      setError(detail || 'AI assistant is temporarily unavailable. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  function handleOpen() {
    const nextOpen = !open
    setOpen(nextOpen)
    if (!open && messages.length === 0) {
      void askAssistant('')
    }
  }

  function handleAsk(e) {
    e.preventDefault()
    const trimmed = question.trim()
    if (!trimmed || loading) return
    void askAssistant(trimmed)
  }

  return (
    <>
      <button className="ai-fab" onClick={handleOpen} aria-label="Open AI assistant">
        <span className="material-symbols-outlined">auto_awesome</span>
      </button>

      <div className={`ai-panel ${open ? 'open' : ''}`}>
        <div className="ai-panel-header">
          <div>
            <h3>AI Explanation Assistant</h3>
            <p>Explains the latest decision in simple language.</p>
          </div>
          <button className="ai-close" onClick={() => setOpen(false)} aria-label="Close assistant">
            <span className="material-symbols-outlined">close</span>
          </button>
        </div>

        <div className="ai-panel-body">
          {messages.length === 0 && !loading && !error && (
            <div className="ai-empty">Ask me to explain the latest decision.</div>
          )}

          {messages.map((msg, index) => (
            <div key={index} className={`ai-msg ${msg.role}`}>
              {msg.text.split('\n').map((line, i) => (
                <div key={i}>{line}</div>
              ))}
            </div>
          ))}

          {loading && <div className="ai-empty">Thinking...</div>}
          {error && <div className="ai-error">{error}</div>}
        </div>

        <form className="ai-panel-footer" onSubmit={handleAsk}>
          <input
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder="Ask a follow-up question"
            disabled={loading}
          />
          <button type="submit" disabled={loading || !question.trim()}>
            <span className="material-symbols-outlined">send</span>
          </button>
        </form>
      </div>
    </>
  )
}
