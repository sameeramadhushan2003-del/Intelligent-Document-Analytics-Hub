import "./Login.css";
import { useRef, useState } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router";
import { useAuth } from "../context/authContext.js";

import { intendedRoute } from "../context/authSession.js";
import "../components/AuthLayout.css";

function Login() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const { login, isAuthenticated, initializing } = useAuth();
  const location = useLocation();
  const submitting = useRef(false);
  const destination = intendedRoute(location.state?.from);
  const navigate = useNavigate();

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting.current) return;
    submitting.current = true;

    setError("");
    setLoading(true);

    try {
      const account = await login(email.trim(), password);
      if (!account) return;

      // Redirect after successful authentication
      navigate(destination, { replace: true });

    } catch (err) {
      setError(
        err.message || "Unable to log in. Please try again."
      );

    } finally {
      submitting.current = false;
      setLoading(false);
    }
  }

  if (isAuthenticated) return <Navigate to={destination} replace />;

  return (
    <main id="main-content" className="auth-container auth-page">
      <div className="auth-card">

        <span className="eyebrow">YOUR KNOWLEDGE AWAITS</span>
        <h1>Welcome back.</h1>

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

          {error && (
            <p
              role="alert"
              className="form-message"
            >
              {error}
            </p>
          )}

          <button
            type="submit"
            className="login-submit-button"
            disabled={loading || initializing}
          >
            {loading ? "Signing in..." : "Sign In"}
          </button>

        </form>

        <p className="auth-footer">
          Don't have an account?{" "}
          <Link to="/register">Create Account</Link>
        </p>

      </div>
    </main>
  );
}

export default Login;
