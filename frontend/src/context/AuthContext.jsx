import { useEffect, useState, useSyncExternalStore } from "react";
import { AuthContext } from "./authContext.js";
import { browserSessionStorage, createAuthSession, tokenExpiresAt } from "./authSession.js";
export function AuthProvider({ children }) {
  const [session] = useState(() => createAuthSession(browserSessionStorage()));
  const state = useSyncExternalStore(session.subscribe, session.getSnapshot);
  useEffect(() => {
    const controller = new AbortController();
    session.restore(controller.signal);
    return () => controller.abort();
  }, [session]);
  useEffect(() => {
    if (!state.accessToken) return;
    const timer = setTimeout(session.logout, Math.max(0, tokenExpiresAt(state.accessToken) - Date.now()));
    return () => clearTimeout(timer);
  }, [session, state.accessToken]);
  return <AuthContext.Provider value={{ ...state, isAuthenticated: state.status === "authenticated", initializing: state.status === "loading", login: session.login, logout: session.logout, retryRestore: session.restore }}>{children}</AuthContext.Provider>;
}
