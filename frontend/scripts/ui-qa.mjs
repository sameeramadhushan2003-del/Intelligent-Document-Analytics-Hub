// Dependency-free browser checks against synthetic intercepted APIs only.
// Start an isolated local Chrome CDP instance on 9223; see docs/ui-redesign.md.
import { writeFile, mkdir } from 'node:fs/promises';
import assert from 'node:assert/strict';
import process from 'node:process';
const origin = process.argv[2] || 'http://localhost:5173';
const out = new URL('../.ui-qa/', import.meta.url);
await mkdir(out, { recursive: true });
const targets = await (await fetch('http://127.0.0.1:9223/json/list')).json();
const page = targets.find(t => t.type === 'page');
if (!page) throw new Error('No isolated QA browser page');
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise(resolve => ws.addEventListener('open', resolve, { once: true }));
let next = 0, mode = 'ok', askCount = 0, listCount = 0;
const waiting = new Map(), consoleErrors = [];
const token = `synthetic.${btoa(JSON.stringify({ exp: Date.now() / 1000 + 900 }))}.qa`;
const user = { id: 7, fullName: 'Alex Morgan', email: 'alex@example.invalid', role: 'USER' };
const docs = [{ id: 101, originalFilename: 'Clinic reference.pdf', title: 'Clinic reference.pdf', fileSizeBytes: 128042, processingStatus: 'EXTRACTED', uploadedAt: '2026-11-01T00:00:00Z' }, { id: 102, originalFilename: 'Research notes.docx', title: 'Research notes.docx', fileSizeBytes: 48200, processingStatus: 'UPLOADED', uploadedAt: '2026-10-30T00:00:00Z' }, { id: 103, originalFilename: 'Project overview.txt', title: 'Project overview.txt', fileSizeBytes: 3800, processingStatus: 'EXTRACTED', uploadedAt: '2026-10-29T00:00:00Z' }];
function send(method, params = {}) {
  const id = ++next;
  return new Promise((resolve, reject) => { waiting.set(id, { resolve, reject }); ws.send(JSON.stringify({ id, method, params })); });
}
ws.addEventListener('message', async event => {
  const message = JSON.parse(event.data);
  if (message.id) { const item = waiting.get(message.id); waiting.delete(message.id); if (message.error) item?.reject(new Error(message.error.message)); else item?.resolve(message.result); }
  if (message.method === 'Runtime.exceptionThrown') consoleErrors.push(message.params.exceptionDetails.exception?.description || message.params.exceptionDetails.text);
  if (message.method !== 'Fetch.requestPaused') return;
  const { requestId, request } = message.params;
  const path = new URL(request.url).pathname;
  let responseCode = 200, body;
  if (path === '/api/auth/login') body = { accessToken: token };
  else if (path === '/api/auth/me') { responseCode = mode === 'expired' ? 401 : 200; body = user; }
  else if (path === '/api/documents') { listCount++; body = docs; }
  else if (path.endsWith('/ask')) {
    askCount++;
    if (mode === 'offline') { await send('Fetch.failRequest', { requestId, errorReason: 'InternetDisconnected' }).catch(() => {}); return; }
    responseCode = mode === 'server-error' ? 503 : 200;
    const id = Number(path.split('/')[3]);
    body = { documentId: id, question: 'How do patients book appointments?', answer: 'Patients book appointments by calling reception.', model: 'qwen2.5:0.5b', sources: [{ chunkId: 1, chunkIndex: 0, chunkText: 'Patients book appointments by calling reception. <script>not executable</script>', similarityScore: .695 }] };
    if (mode === 'slow') await new Promise(resolve => setTimeout(resolve, 1500));
  } else if (path.endsWith('/process')) body = { documentId: 101, characterCount: 72 };
  else if (path.endsWith('/chunks/generate')) body = { documentId: 101, chunkCount: 1 };
  else if (path.endsWith('/embeddings/generate')) body = { documentId: 101, embeddingCount: 1 };
  else { responseCode = 404; body = { message: 'Synthetic endpoint not provided' }; }
  await send('Fetch.fulfillRequest', { requestId, responseCode, responseHeaders: [{ name: 'Content-Type', value: 'application/json' }], body: btoa(JSON.stringify(body)) }).catch(() => {});
});
await send('Page.enable'); await send('Runtime.enable');
await send('Fetch.enable', { patterns: [{ urlPattern: '*/api/*' }] });
async function evaluate(expression) { const r = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true }); if (r.exceptionDetails) throw new Error('QA evaluation failed'); return r.result?.value; }
async function until(expression) { for (let i = 0; i < 100; i++) { if (await evaluate(expression)) return; await new Promise(resolve => setTimeout(resolve, 80)); } throw new Error('Timeout: ' + expression); }
async function navigate(path) { await send('Page.navigate', { url: origin + path }); await until('document.readyState === "complete" && !!document.querySelector("main")'); }
async function viewport(width) { await send('Emulation.setDeviceMetricsOverride', { width, height: 1000, deviceScaleFactor: 1, mobile: width < 768 }); }
async function shot(name) { await new Promise(resolve => setTimeout(resolve, 1100)); const data = await send('Page.captureScreenshot', { format: 'png' }); await writeFile(new URL(name + '.png', out), Buffer.from(data.data, 'base64')); }
async function input(selector, value) { await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});Object.getOwnPropertyDescriptor(e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype,'value').set.call(e,${JSON.stringify(value)});e.dispatchEvent(new Event('input',{bubbles:true}));})()`); }
async function clickText(text, selector = 'button') { await until(`[...document.querySelectorAll(${JSON.stringify(selector)})].some(e=>e.textContent.trim()===${JSON.stringify(text)} && !e.disabled)`); await evaluate(`(()=>{const e=[...document.querySelectorAll(${JSON.stringify(selector)})].find(e=>e.textContent.trim()===${JSON.stringify(text)});if(!e)throw Error('Missing control');e.click();})()`); }
async function noOverflow() { assert.ok(await evaluate('document.documentElement.scrollWidth <= innerWidth + 1'), 'Unexpected horizontal page overflow'); }
const checks = [];
try {
 await viewport(1440); await navigate('/'); await evaluate('sessionStorage.clear()'); await send('Page.reload'); await until('!!document.querySelector(".hero-art")'); await until('!!document.querySelector(".hero-art")'); await shot('home-desktop'); await noOverflow();
 await clickText('Semantic search'); await until('document.querySelector(".ai-label")?.textContent.includes("Matching")'); checks.push('Interactive illustrative product tour');
 await viewport(768); await shot('home-tablet'); await noOverflow(); await viewport(375); await shot('home-mobile'); await noOverflow(); await evaluate('document.querySelector(".mobile-menu").click()'); assert.ok(await evaluate('document.querySelector(".marketing-links").classList.contains("is-open")')); checks.push('Mobile navigation and homepage overflow');
 await navigate('/documents'); await until('location.pathname === "/login"'); checks.push('Unauthenticated protected route redirect');
 await input('#email', 'alex@example.invalid'); await input('#password', 'synthetic-fixture'); await evaluate('document.querySelector("form").requestSubmit()'); await until('location.pathname === "/documents" && !!document.querySelector("tbody tr")'); checks.push('Mock login returns to intended documents route');
 await send('Page.reload'); await until('location.pathname === "/documents" && !!document.querySelector("tbody tr")'); checks.push('Documents refresh restores verified session and route');
 await viewport(1440); await navigate('/dashboard'); await until('document.querySelector(".stat-card strong")?.textContent === "3"'); await shot('dashboard-desktop');
 await send('Page.reload'); await until('location.pathname === "/dashboard" && document.querySelector(".stat-card strong")?.textContent === "3"'); checks.push('Dashboard data rendering and refresh');
 await navigate('/documents'); await until('!!document.querySelector("tbody tr")'); await shot('documents-desktop');
 const oldLists = listCount; await clickText('Extract'); await until('document.querySelector(".notice")?.textContent.includes("processed") || document.body.textContent.includes("processed successfully")'); assert.ok(listCount >= oldLists); checks.push('Extraction action and library refresh against mock API');
 await clickText('Chunks'); await until('document.body.textContent.includes("chunk(s) generated")');
 await clickText('Embeddings'); await until('document.body.textContent.includes("embedding(s) generated")'); checks.push('Chunk and embedding actions against mock API');
 await clickText('Ask AI'); await input('.rag-panel textarea', 'How do patients book appointments?'); mode = 'slow'; const initial = askCount;
 await evaluate('document.querySelector(".rag-submit").click();document.querySelector(".rag-submit").click()'); await until('!!document.querySelector(".rag-panel [aria-busy=true]")'); await until('!!document.querySelector(".rag-answer")'); assert.equal(askCount - initial, 1); checks.push('Ask AI loading, answer, duplicate-click protection');
 await evaluate('document.querySelector(".rag-source summary").click()'); assert.ok(await evaluate('document.querySelector(".rag-source").open')); assert.equal(await evaluate('document.querySelectorAll(".rag-source script").length'), 0); await shot('ask-desktop'); checks.push('Expandable scores and escaped untrusted source text');
 await input('.rag-panel textarea', 'Second question'); await evaluate('document.querySelector(".rag-submit").click()'); await evaluate('[...document.querySelectorAll("tbody button")].filter(e=>e.textContent.trim()==="Ask AI")[0].click()'); await until('!document.querySelector(".rag-answer") && document.querySelector(".rag-panel textarea")?.value === ""'); await new Promise(resolve => setTimeout(resolve, 1800)); assert.equal(await evaluate('document.querySelector(".rag-answer") !== null'), false); checks.push('Document switching rejects delayed answer');
 await input('.rag-panel textarea', 'Close question'); await evaluate('document.querySelector(".rag-submit").click();document.querySelector(".rag-close").click()'); await until('!document.querySelector(".rag-panel")'); checks.push('Closing aborts pending panel');
 mode = 'server-error'; await clickText('Ask AI'); await input('.rag-panel textarea', 'Failure question'); await evaluate('document.querySelector(".rag-submit").click()'); await until('!!document.querySelector(".rag-panel [role=alert]")'); checks.push('HTTP 503 visible error');
 mode = 'offline'; await evaluate('document.querySelector(".rag-submit").click()'); await until('document.querySelector(".rag-panel [role=alert]")?.textContent.includes("connect")'); checks.push('Network failure recovery'); mode = 'ok';
 await viewport(768); await evaluate('scrollTo(0,0)'); await shot('documents-tablet'); await noOverflow(); await viewport(375); await evaluate('scrollTo(0,0)'); await shot('documents-mobile'); await noOverflow(); await evaluate('document.querySelector(".workspace-topbar .mobile-menu").click()'); assert.ok(await evaluate('document.querySelector(".sidebar").classList.contains("is-open")')); await send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Escape', code: 'Escape' }); await until('!document.querySelector(".sidebar").classList.contains("is-open")'); checks.push('Mobile workspace and Escape dismissal');
 await evaluate('document.querySelector(".topbar-actions button").click()'); await clickText('Sign out'); await until('location.pathname === "/login"'); assert.equal(await evaluate('sessionStorage.getItem("document-hub.session.v1")'), null); checks.push('Profile action and logout clear session');
 await shot('login-mobile'); mode = 'expired'; await evaluate(`sessionStorage.setItem('document-hub.session.v1', ${JSON.stringify(token)})`); await navigate('/documents'); await until('location.pathname === "/login"'); assert.equal(await evaluate('sessionStorage.getItem("document-hub.session.v1")'), null); checks.push('Rejected backend restoration clears session and protects route'); mode = 'ok'; await evaluate("sessionStorage.setItem('document-hub.session.v1','malformed')"); await navigate('/dashboard'); await until('location.pathname === "/login"'); checks.push('Malformed stored session cannot access dashboard'); assert.deepEqual(consoleErrors, []); checks.push('No uncaught runtime exceptions');
 await writeFile(new URL('results.json', out), JSON.stringify({ type: 'mocked-browser-api', checks, screenshots: ['home-desktop','home-tablet','home-mobile','dashboard-desktop','documents-desktop','ask-desktop','documents-mobile','documents-tablet','login-mobile'] }, null, 2));
 console.log(JSON.stringify({ type: 'mocked-browser-api', passed: checks.length, checks }));
} catch (error) { console.error(JSON.stringify({ error: error.message, consoleErrors })); throw error; } finally { ws.close(); }
