export const SESSION_KEY = "document-hub.session.v1";
export function tokenExpiresAt(token) {
  try {
    const part = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const payload = JSON.parse(atob(part));
    return Number.isFinite(payload.exp) ? payload.exp * 1000 : 0;
  } catch { return 0; }
}
export function intendedRoute(path) {
  return typeof path === "string" && /^\/(dashboard|documents)([?#].*)?$/.test(path) ? path : "/dashboard";
}
export function routeAccess(status) {
  return status === "authenticated" ? "allow" : status === "anonymous" ? "redirect" : "wait";
}
export function browserSessionStorage() { try { return window.sessionStorage; } catch { return null; } }
export function createAuthSession(storage, fetchImpl = fetch, now = Date.now) {
  let state = { status: "loading", user: null, accessToken: null, error: "", notice: "" };
  let generation = 0;
  const listeners = new Set();
  const update = (value) => { state = { ...state, ...value }; listeners.forEach((fn) => fn()); };
  const read = () => { try { return storage?.getItem(SESSION_KEY); } catch { return null; } };
  const clear = () => { try { storage?.removeItem(SESSION_KEY); } catch { /* Storage may be unavailable. */ } };
  const logout = () => { generation++; clear(); update({ status: "anonymous", user: null, accessToken: null, error: "", notice: "" }); };
  async function verify(token, signal) {
    const response = await fetchImpl("/api/auth/me", { headers: { Authorization: `Bearer ${token}` }, signal });
    if (!response.ok) {
      const error = new Error(response.status === 401 || response.status === 403 ? "Your session has ended. Please sign in again." : "Unable to restore your session. Check the server and retry.");
      error.unauthorized = response.status === 401 || response.status === 403;
      throw error;
    }
    const user = await response.json();
    if (!user || !user.id || typeof user.fullName !== "string" || typeof user.email !== "string") throw new Error("Unable to verify your account.");
    return user;
  }
  return {
    getSnapshot: () => state,
    subscribe: (fn) => { listeners.add(fn); return () => listeners.delete(fn); },
    logout,
    async restore(signal) {
      const current = ++generation;
      const token = read();
      if (!token || tokenExpiresAt(token) <= now()) { logout(); return; }
      update({ status: "loading", error: "" });
      try {
        const user = await verify(token, signal);
        if (current === generation && !signal?.aborted) update({ status: "authenticated", user, accessToken: token });
      } catch (error) {
        if (current !== generation || signal?.aborted) return;
        if (error.unauthorized) logout();
        else update({ status: "error", error: "We couldn't connect to verify your session. Your documents remain protected. Please retry." });
      }
    },
    async login(email, password) {
      const current = ++generation;
      const response = await fetchImpl("/api/auth/login", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email, password }) });
      const data = await response.json().catch(() => null);
      if (!response.ok) throw new Error(response.status === 401 ? "Incorrect email or password." : "Unable to sign in. Check your details and try again.");
      if (!data?.accessToken || tokenExpiresAt(data.accessToken) <= now()) throw new Error("Unable to establish a valid session. Please try again.");
      const user = await verify(data.accessToken);
      if (current !== generation) return null;
      let notice = "";
      try {
        if (!storage) throw new Error("Storage unavailable");
        storage.setItem(SESSION_KEY, data.accessToken);
      } catch { notice = "This browser cannot save your tab session. Refreshing will require signing in again."; }
      update({ status: "authenticated", user, accessToken: data.accessToken, error: "", notice });
      return user;
    },
  };
}
