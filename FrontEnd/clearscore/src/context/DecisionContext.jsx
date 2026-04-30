/* @refresh reload */
import { createContext, useContext, useMemo, useState } from 'react'

const DecisionContext = createContext(null)

export function DecisionProvider({ children }) {
  const [latestDecision, setLatestDecision] = useState(null)

  const value = useMemo(() => ({ latestDecision, setLatestDecision }), [latestDecision])
  return <DecisionContext.Provider value={value}>{children}</DecisionContext.Provider>
}

export function useDecision() {
  const ctx = useContext(DecisionContext)
  if (!ctx) throw new Error('useDecision must be used within DecisionProvider')
  return ctx
}
