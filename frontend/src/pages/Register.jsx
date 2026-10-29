
import { useState } from 'react'
import { Link } from 'react-router'
import './Register.css'

function Register() {
  const [form, setForm] = useState({
    fullName: '',
    email: '',
    password: '',
    confirmPassword: '',
  })

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  function handleChange(event) {
    const { name, value } = event.target

    setForm((previous) => ({
      ...previous,
      [name]: value,
    }))
  }

  async function handleSubmit(event) {
    event.preventDefault()

    setError('')
    setSuccess('')

    if (!form.fullName.trim()) {
      setError('Please enter your full name.')
      return
    }

    if (form.password !== form.confirmPassword) {
      setError('Passwords do not match.')
      return
    }

    if (form.password.length < 8) {
      setError('Password must contain at least 8 characters.')
      return
    }

    if (new TextEncoder().encode(form.password).length > 72) {
      setError('Password must not exceed 72 UTF-8 bytes.')
      return
    }

    setLoading(true)

    try {
      const response = await fetch('/api/auth/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          fullName: form.fullName.trim(),
          email: form.email.trim(),
          password: form.password,
        }),
      })

      const data = await response.json().catch(() => null)

      if (!response.ok) {
        throw new Error(
          data?.message || `Registration failed (HTTP ${response.status}).`
        )
      }

      setSuccess(
        `Account created successfully for ${data?.email || form.email}.`
      )

      setForm((previous) => ({
        ...previous,
        password: '',
        confirmPassword: '',
      }))
    } catch (err) {
      if (err instanceof TypeError) {
        setError('Cannot connect to the server. Please try again.')
      } else {
        setError(err.message || 'Registration failed.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
      <main className="register-page">
        <section className="register-card">
          <div className="register-heading">
            <span className="register-eyebrow">
              Intelligent Document Analytics Hub
            </span>

            <h1>Create your account</h1>

            <p>
              Register to access your personal document
              analytics workspace.
            </p>
          </div>

          {error && (
            <div className="register-error" role="alert">
              {error}
            </div>
          )}

          {success && (
            <div className="register-success" role="status">
              {success}
            </div>
          )}

          <form className="register-form" onSubmit={handleSubmit}>
            <label htmlFor="fullName">Full name</label>
            <input
              id="fullName"
              name="fullName"
              type="text"
              placeholder="Enter your full name"
              value={form.fullName}
              onChange={handleChange}
              maxLength={150}
              autoComplete="name"
              required
            />

            <label htmlFor="email">Email address</label>
            <input
              id="email"
              name="email"
              type="email"
              placeholder="Enter your email address"
              value={form.email}
              onChange={handleChange}
              maxLength={320}
              autoComplete="email"
              required
            />

            <label htmlFor="password">Password</label>
            <input
              id="password"
              name="password"
              type="password"
              placeholder="Create a password"
              value={form.password}
              onChange={handleChange}
              minLength={8}
              maxLength={72}
              autoComplete="new-password"
              required
            />

            <label htmlFor="confirmPassword">
              Confirm password
            </label>
            <input
              id="confirmPassword"
              name="confirmPassword"
              type="password"
              placeholder="Confirm your password"
              value={form.confirmPassword}
              onChange={handleChange}
              autoComplete="new-password"
              required
            />

            <button
              className="register-button"
              type="submit"
              disabled={loading}
            >
              {loading ? 'Creating account...' : 'Create account'}
            </button>
          </form>

          <p className="register-footer">
            Already have an account? <Link to="/login">Sign in</Link>
          </p>
        </section>
      </main>
  )
}

export default Register
