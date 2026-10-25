
import { Link } from 'react-router'

function Login() {
  return (
    <main className="page-shell">
      <span className="tag">Authentication</span>

      <h1>Sign In</h1>

      <p>
        Welcome back to Intelligent Document Analytics Hub.
      </p>

      <p>
        The login form will be implemented in our
        authentication milestone.
      </p>

      <p>
        Don't have an account?{' '}
        <Link to="/register">Create an account</Link>
      </p>
    </main>
  )
}

export default Login
