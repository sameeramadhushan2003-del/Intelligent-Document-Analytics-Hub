import test from "node:test";
import assert from "node:assert/strict";
import { createAuthSession, SESSION_KEY, routeAccess, intendedRoute, tokenExpiresAt } from "./authSession.js";
const token = (exp = Date.now() / 1000 + 900) => `synthetic.${btoa(JSON.stringify({ exp }))}.test`;
const account = { id: 7, fullName: "Synthetic Student", email: "student@example.invalid", role: "USER" };
function storage(initial) { const map = new Map(initial ? [[SESSION_KEY, initial]] : []); return { getItem: key => map.get(key) || null, setItem: (key, value) => map.set(key, value), removeItem: key => map.delete(key) }; }

test("refresh waits for backend verification then restores the session", async () => {
  const saved = token(); const tab = storage(saved); let release;
  const session = createAuthSession(tab, () => new Promise(resolve => { release = resolve; }));
  const pending = session.restore();
  assert.equal(routeAccess(session.getSnapshot().status), "wait");
  release(Response.json(account)); await pending;
  assert.equal(session.getSnapshot().accessToken, saved);
  assert.equal(routeAccess(session.getSnapshot().status), "allow");
  const refreshed = createAuthSession(tab, async () => Response.json(account));
  await refreshed.restore(); assert.equal(refreshed.getSnapshot().user.id, 7);
});

test("missing, expired and malformed tokens clear persistence before any private request", async () => {
  for (const saved of [null, "invalid", token(Date.now() / 1000 - 50)]) {
    let calls = 0; const tab = storage(saved);
    const session = createAuthSession(tab, async () => { calls++; });
    await session.restore(); assert.equal(calls, 0);
    assert.equal(routeAccess(session.getSnapshot().status), "redirect");
    assert.equal(tab.getItem(SESSION_KEY), null);
  }
});

test("invalid backend session clears saved credentials", async () => {
  for (const status of [401, 403]) {
    const tab = storage(token()); const session = createAuthSession(tab, async () => new Response(null, { status }));
    await session.restore(); assert.equal(session.getSnapshot().status, "anonymous"); assert.equal(tab.getItem(SESSION_KEY), null);
  }
});

test("temporary server failure blocks private routes and supports retry without destroying the session", async () => {
  const tab = storage(token()); let failed = true;
  const session = createAuthSession(tab, async () => { if (failed) throw new TypeError("Offline"); return Response.json(account); });
  await session.restore(); assert.equal(session.getSnapshot().status, "error"); assert.equal(routeAccess(session.getSnapshot().status), "wait"); assert.ok(tab.getItem(SESSION_KEY));
  failed = false; await session.restore(); assert.equal(session.getSnapshot().status, "authenticated");
});

test("login persists only after account verification and logout clears it", async () => {
  const saved = token(); const tab = storage(); const calls = [];
  const session = createAuthSession(tab, async url => { calls.push(url); return Response.json(url.endsWith("/login") ? { accessToken: saved } : account); });
  await session.login("student@example.invalid", "synthetic-only");
  assert.deepEqual(calls, ["/api/auth/login", "/api/auth/me"]); assert.equal(tab.getItem(SESSION_KEY), saved);
  session.logout(); assert.equal(tab.getItem(SESSION_KEY), null); assert.equal(session.getSnapshot().user, null); assert.equal(session.getSnapshot().accessToken, null);
});

test("failed login or unverified account never persists a token", async () => {
  for (const status of [401, 500]) {
    const tab = storage(); const session = createAuthSession(tab, async () => new Response(null, { status }));
    await assert.rejects(session.login("student@example.invalid", "synthetic-only")); assert.equal(tab.getItem(SESSION_KEY), null);
  }
  const tab = storage(); const session = createAuthSession(tab, async url => url.endsWith("/login") ? Response.json({ accessToken: token() }) : new Response(null, { status: 401 }));
  await assert.rejects(session.login("student@example.invalid", "synthetic-only")); assert.equal(tab.getItem(SESSION_KEY), null);
});

test("logout wins over a pending restoration", async () => {
  let release; const tab = storage(token()); const session = createAuthSession(tab, () => new Promise(resolve => { release = resolve; }));
  const pending = session.restore(); session.logout(); release(Response.json(account)); await pending;
  assert.equal(session.getSnapshot().status, "anonymous"); assert.equal(tab.getItem(SESSION_KEY), null);
});

test("cancelled restoration cannot authenticate a mounted replacement", async () => {
  let release; const session = createAuthSession(storage(token()), () => new Promise(resolve => { release = resolve; }));
  const controller = new AbortController(); const pending = session.restore(controller.signal); controller.abort(); release(Response.json(account)); await pending;
  assert.notEqual(session.getSnapshot().status, "authenticated");
});

test("storage failure allows current-session login with an honest persistence notice", async () => {
  const session = createAuthSession(null, async url => Response.json(url.endsWith("/login") ? { accessToken: token() } : account));
  await session.login("student@example.invalid", "synthetic-only"); assert.equal(session.getSnapshot().status, "authenticated"); assert.ok(session.getSnapshot().notice);
});

test("only implemented internal intended routes are restored", () => {
  for (const path of ["/documents", "/dashboard", "/documents?tool=ask#upload"]) assert.equal(intendedRoute(path), path);
  for (const path of ["https://example.invalid", "//example.invalid", "/login", null, "/documents/evil"]) assert.equal(intendedRoute(path), "/dashboard");
  assert.equal(tokenExpiresAt("malformed"), 0);
});
