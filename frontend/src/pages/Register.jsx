
import { useState } from 'react'
import { Link } from 'react-router'

function Register() {
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  const handleSubmit = (event) => {
    event.preventDefault()

    setError('')
    setMessage('')

    if (fullName.trim() === '') {
      setError('Please enter your full name.')
      return
    }

    if (password !== confirmPassword) {
      setError('Passwords do not match.')
      return
    }

    setMessage(
      'Frontend validation passed. Backend registration is not connected yet.'
    )
  }

  return (
    <main className="auth-container">
      <div className="auth-card">
        <h1>Create Account</h1>

        <p>Join Intelligent Document Analytics Hub</p>

        <form onSubmit={handleSubmit}>

          <div className="form-group">
            <label htmlFor="fullName">Full Name</label>

            <input
              id="fullName"
              type="text"
              placeholder="Enter your full name"
              value={fullName}
              onChange={(event) => setFullName(event.target.value)}
              autoComplete="name"
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="registerEmail">Email Address</label>

            <input
              id="registerEmail"
              type="email"
              placeholder="Enter your email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="registerPassword">Password</label>

            <input
              id="registerPassword"
              type="password"
              placeholder="Minimum 8 characters"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              minLength={8}
              autoComplete="new-password"
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="confirmPassword">Confirm Password</label>

            <input
              id="confirmPassword"
              type="password"
              placeholder="Re-enter your password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              minLength={8}
              autoComplete="new-password"
              required
            />
          </div>

          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}

          {message && (
            <p className="form-success" role="status">
              {message}
            </p>
          )}

          <button type="submit" className="btn btn-primary auth-submit">
            Create Account
          </button>
        </form>

        <p className="auth-footer">
          Already have an account?{' '}
          <Link to="/login">Sign In</Link>
        </p>
      </div>
    </main>
  )
}

export default Register
