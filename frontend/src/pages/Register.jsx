
import { Link } from 'react-router'

function Register() {
  return (
    <main className="page-shell">
      <span className="tag">Join our platform</span>

      <h1>Create Account</h1>

      <p>
        Create your account to manage documents and
        access intelligent document analysis.
      </p>

      <p>
        The registration form will be implemented
        in our authentication milestone.
      </p>

      <p>
        Already have an account?{' '}
        <Link to="/login">Sign in</Link>
      </p>
    </main>
  )
}

export default Register
