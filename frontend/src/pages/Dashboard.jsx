import { useAuth } from "../context/AuthContext.jsx";
import { useNavigate } from "react-router";
import { useEffect, useState } from 'react'
import { Link } from 'react-router'

function Dashboard() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  function handleLogout() {
    logout();
    navigate("/login", { replace: true });
  }
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

    function handleLogout() {
      logout();
      navigate("/login", { replace: true });
    }

    return () => controller.abort()
  }, [])

  return (
    <main className="page-shell">

      
    <section
      className="dashboard-user-section"
      style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        gap: "16px",
        padding: "20px",
        marginBottom: "24px",
        background: "#eff6ff",
        border: "1px solid #bfdbfe",
        borderRadius: "12px",
      }}
    >
      <div>
        <h2 style={{ margin: "0 0 8px", color: "#1e3a8a" }}>
          Welcome, {user?.fullName || "User"}!
        </h2>

        <p style={{ margin: "0 0 4px", color: "#334155" }}>
          {user?.email}
        </p>

        <span style={{ fontSize: "13px", color: "#64748b" }}>
          Account role: {user?.role}
        </span>
      </div>

      <button
        type="button"
        onClick={handleLogout}
        style={{
          background: "#dc2626",
          color: "#ffffff",
          border: "none",
          padding: "12px 22px",
          borderRadius: "8px",
          fontWeight: "600",
          cursor: "pointer",
        }}
      >
        Logout
      </button>
    </section>

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
