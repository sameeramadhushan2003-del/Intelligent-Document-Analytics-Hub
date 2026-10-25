
import { Link } from 'react-router'

function Dashboard() {
  return (
    <main className="page-shell">
      <span className="tag">Dashboard Preview</span>

      <h1>Document Analytics Dashboard</h1>

      <p>
        This is the future workspace for managing
        documents and exploring AI-powered insights.
      </p>

      <p>
        Document statistics, uploads, AI chat, and
        analytics will be implemented in later milestones.
      </p>

      <Link to="/" className="btn btn-primary">
        Back to Home
      </Link>
    </main>
  )
}

export default Dashboard
