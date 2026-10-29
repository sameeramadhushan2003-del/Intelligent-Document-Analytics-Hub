import "./Login.css";
import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { useAuth } from "../context/AuthContext.jsx";

function Login() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const { login } = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(event) {
    event.preventDefault();

    setError("");
    setLoading(true);

    try {
      await login(email.trim(), password);

      // Redirect after successful authentication
      navigate("/dashboard", { replace: true });

    } catch (err) {
      setError(
        err.message || "Unable to log in. Please try again."
      );

    } finally {
      setLoading(false);
    }
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

          {error && (
            <p
              role="alert"
              className="form-message"
              style={{
                color: "#dc2626",
                marginTop: "10px",
              }}
            >
              {error}
            </p>
          )}

        <button
          type="submit"
          className="login-submit-button"
          disabled={loading}
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
