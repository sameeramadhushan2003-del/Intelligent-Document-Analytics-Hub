
import { useEffect, useState } from 'react'
import { Link } from 'react-router'

function Dashboard() {
  const [health, setHealth] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()

    async function loadHealth() {
      try {
        const response = await fetch('/api/health', {
          signal: controller.signal,
        })

        if (!response.ok) {
          throw new Error(
            `Backend returned HTTP ${response.status}`
          )
        }

        const data = await response.json()

        setHealth(data)
      } catch (err) {
        if (err.name !== 'AbortError') {
          setError(err.message)
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false)
        }
      }
    }

    loadHealth()

    return () => controller.abort()
  }, [])

  return (
    <main className="page-shell">
      <span className="tag">Full-Stack Integration</span>

      <h1>Document Analytics Dashboard</h1>

      <p>
        This dashboard is now connected to our
        Spring Boot backend.
      </p>

      <section className="backend-status">
        <h2>Backend Connection Status</h2>

        {loading && (
          <p role="status">Checking backend connection...</p>
        )}

        {error && (
          <p className="status-error" role="alert">
            Connection failed: {error}
          </p>
        )}

        {health && (
          <div className="status-success" role="status">
            <h3>
              {health.status === 'UP'
                ? 'Backend Online'
                : 'Backend Status Unknown'}
            </h3>

            <p>Status: {health.status}</p>
            <p>Application: {health.application}</p>
          </div>
        )}
      </section>

      <p>
        Document management and analytics features
        will be added in the next milestones.
      </p>

      <Link to="/" className="btn btn-primary">
        Back to Home
      </Link>
    </main>
  )
}

export default Dashboard
