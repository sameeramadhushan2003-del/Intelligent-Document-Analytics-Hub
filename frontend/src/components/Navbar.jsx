
function Navbar() {
  return (
    <header className="navbar">
      <a href="#home" className="brand">
        <span className="brand-icon">D</span>
        <span>Document Analytics Hub</span>
      </a>

      <nav className="nav-links" aria-label="Main navigation">
        <a href="#home">Home</a>
        <a href="#features">Features</a>
        <a href="#workflow">How It Works</a>
      </nav>

      <button
        className="btn btn-outline"
        type="button"
        disabled
        title="Authentication will be implemented soon"
      >
        Sign In (Soon)
      </button>
    </header>
  )
}

export default Navbar
