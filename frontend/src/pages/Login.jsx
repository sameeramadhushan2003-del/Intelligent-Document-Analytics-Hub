
import { useState } from 'react'
import { Link } from 'react-router'

function Login() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [message, setMessage] = useState('')

  const handleSubmit = (event) => {
    event.preventDefault()

    setMessage(
      'Form validation passed. Backend authentication is not connected yet.'
    )
  }

  return (
    <main className="auth-container">
      <div className="auth-card">
        <h1>Welcome Back</h1>

        <p>
          Sign in to Intelligent Document Analytics Hub
        </p>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="email">Email Address</label>

            <input
              id="email"
              type="email"
              placeholder="Enter your email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="password">Password</label>

            <input
              id="password"
              type="password"
              placeholder="Enter your password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              required
            />
          </div>

          <button type="submit" className="btn btn-primary auth-submit">
            Sign In
          </button>
        </form>

        {message && <p className="form-message" role="status">{message}</p>}

        <p className="auth-footer">
          Don't have an account?{' '}
          <Link to="/register">Create Account</Link>
        </p>
      </div>
    </main>
  )
}

export default Login
