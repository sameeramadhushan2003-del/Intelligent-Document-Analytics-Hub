
import { Link, NavLink } from 'react-router'

function Navbar() {
  return (
    <header className="navbar">
      <Link to="/" className="brand">
        <span className="brand-icon">D</span>
        <span>Document Analytics Hub</span>
      </Link>

      <nav className="nav-links" aria-label="Main navigation">
        <NavLink to="/" end>
          Home
        </NavLink>

        <NavLink to="/dashboard">
          Dashboard Demo
        </NavLink>
      </nav>

      <Link to="/login" className="btn btn-outline">
        Sign In
      </Link>
    </header>
  )
}

export default Navbar
