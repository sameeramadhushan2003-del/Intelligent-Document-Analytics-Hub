import { Navigate, useLocation } from "react-router";
import { useAuth } from "../context/authContext.js";
import { routeAccess } from "../context/authSession.js";
import Icon from "./Icon.jsx";
export default function ProtectedRoute({ children }) {
  const { status, error, retryRestore, logout } = useAuth();
  const location = useLocation();
  const access = routeAccess(status);
  if (access === "wait") return <main className="session-splash"><div className="brand-mark"><Icon name="spark" /></div><span className="eyebrow">INTELLIGENT DOCUMENT ANALYTICS HUB</span><h1>{error ? "Let's reconnect." : "Opening your workspace."}</h1><p role={error ? "alert" : "status"}>{error || "Securely verifying your session..."}</p>{error ? <div className="button-row"><button className="btn btn-primary" onClick={() => retryRestore()}>Try again</button><button className="btn btn-outline" onClick={logout}>Return to sign in</button></div> : <span className="loader" aria-hidden="true" />}</main>;
  if (access === "redirect") return <Navigate to="/login" replace state={{ from: location.pathname + location.search + location.hash }} />;
  return children;
}
