# Step 34.12: live RAG verification

Verification date: 2026-11-01 (Asia/Colombo).

## Observed environment

- PostgreSQL 18 is running and `pg_isready` reports that localhost:5432 accepts connections.
- Ollama is reachable on localhost:11434. Both `all-minilm:latest` and `qwen2.5:0.5b` are installed.
- A sequential synthetic-fixture check produced a finite 384-dimensional embedding and a nonempty Qwen answer mentioning the fixture's appointment reception desk. Models were unloaded after each request using `keep_alive: 0`.
- Spring Boot startup failed because PostgreSQL rejected the application's configured database credentials. This is the cause of the subsequent Hibernate metadata/dialect error; changing the dialect will not repair authentication.
- Startup was attempted with Flyway disabled and Hibernate schema changes disabled. No database credentials, accounts, schema, or document records were modified.
- Vite starts successfully. Its `/api` proxy returned 502 because Spring Boot was unavailable.
- No automated browser testing tool or installed Playwright/Puppeteer dependency was available. Authenticated live checks were deferred to manual testing at the user's request. Select and verify ownership of any test document through the authenticated library.

## Corrections

- The Maven wrapper indexed `.Target[0]` when PowerShell returned a null target for a regular cache directory. It now checks the target before indexing. Offline wrapper compilation against the existing cached Maven succeeded; Maven was not reinstalled or upgraded.
- Ask AI now uses `/api/documents/{documentId}/ask`, matching the existing Vite proxy used by login and document loading. The answer panel also displays the returned model name.

## Restore startup

1. Restore the correct PostgreSQL application credentials through your existing private configuration or launch setup. Do not paste secrets into chat, commands, screenshots, or this document. Do not change database accounts or reset the database.
2. Start Spring Boot with the project's usual trusted launch configuration. During verification, disable Flyway and keep `spring.jpa.hibernate.ddl-auto=none` if migration execution must be avoided.
3. Confirm `GET http://localhost:8080/api/health` returns 200. Start Vite with `npm run dev` in `frontend`, open `http://localhost:5173`, and confirm `/api/health` through that origin also returns 200.

## Automated validation

- Cached Maven offline compilation: passed. The repaired wrapper also compiled successfully offline with the existing dependency repository explicitly selected.
- Selected backend tests: 42 discovered, 41 passed, 1 skipped, no failures or errors. Covered RAG controller routing/JWT identity, RAG response metadata and insufficient-information fallback, ownership denial before embedding/chat, document processing, chunking/persistence, text extraction, and embedding input validation. The opt-in live Ollama JUnit integration test was skipped; the separate synthetic live model check passed.
- Frontend production build: passed.
- ESLint for `DocumentRagAnswer.jsx` and `Documents.jsx`: passed.
- `git diff --check`: passed.
- Vite HTML response: HTTP 200. Backend/proxy connectivity remains blocked by database authentication.

## Manual authenticated API and browser checks

Use only an approved existing test account. Do not export HAR files or copy Authorization headers; these contain JWTs. Keep private answers and source text out of reports. Record only status codes, source counts, numeric scores, model names, and pass/fail outcomes.

1. **Login and library:** Sign in through the UI. Confirm My Documents loads and existing upload, Process, Generate Chunks, Generate Embeddings, and Search actions remain present. Avoid regenerating existing embeddings or changing documents merely to validate this screen.
2. **Select document:** Choose an approved, already-embedded test document from the authenticated account's library. Do not guess document IDs or bypass ownership.
3. **Grounded answer:** Open Ask AI and enter a question known to be answered by that document. For an appointment fixture, use `How do patients book appointments?`. Confirm a loading message, disabled submit button, then a nonempty answer and model `qwen2.5:0.5b`. In DevTools Network, check the POST path, status 200, and response fields `documentId`, `question`, `answer`, `model`, and `sources` without exporting their contents. Sources should be relevant and have finite cosine similarity scores.
4. **Sources:** Expand and collapse each source using mouse and keyboard. Compare its text privately with the answer. Confirm chunk indices and similarity scores display and long content wraps.
5. **Duplicates:** Double-click Ask AI rapidly. Confirm one POST is sent and submission remains disabled until completion. Retry after completion and confirm a new request can be sent.
6. **Switch during generation:** Submit a question, then open Ask AI for another owned document. Confirm its question and results start empty and the first answer never appears there. The first browser request should be cancelled; cancellation does not guarantee server inference stops immediately. Wait for inference to finish before submitting another question on this 8 GB laptop.
7. **Close during generation:** Submit, close the panel, wait, and reopen it. Confirm the panel closes immediately and no previous answer reappears. Check focus returns to the original row's Ask AI button.
8. **Empty/invalid question:** Confirm the UI disables submission for an empty or whitespace-only question and caps input at 1000 characters. To verify backend validation, use the browser's authenticated request editor (for example Firefox Network > right-click the completed Ask request > Edit and Resend), keeping its existing authentication private, and replace only its body with `{"question":""}`. Expect 400. Do not save or share the request.
9. **Unauthenticated request:** In the request editor, remove the Authorization header and resend. Expect 401, including when the body contains a valid question.
10. **Other user's document:** Only with two approved existing test accounts and a known test document owned by the second account, replay the first account's authenticated request against that document. Expect 403 or 404 and no answer/sources. Do not create accounts or discover private IDs for this test.
11. **Irrelevant question:** Ask a clearly unrelated question, such as `What is the orbital period of Neptune?`, when the selected test document contains only appointment information. Expect an insufficient-information answer. When retrieval finds no sufficiently relevant sources, expect `I could not find that information in the document.` and an empty source list. If unrelated chunks score above the configured threshold, check the model still declines rather than inventing facts; report hallucination as a failed relevance check.
12. **Expired session:** Leave an approved test session open until its existing token expires, then submit. Expect 401 and a sign-in-again message; sign in again to recover. Do not alter signing keys or fabricate tokens.
13. **HTTP errors:** With approved test fixtures only, verify a known absent document returns 404 and a document without embeddings returns 409. A server-side 5xx should show the service-unavailable message. Do not damage documents or stop shared services to force failures; where no safe fixture exists, mark that check untested.
14. **Network failure:** Open Ask AI while connected. Set DevTools Network to Offline, submit, and confirm a connection error and re-enabled controls. Restore Online and retry. This checks browser network failure without changing the database or backend.
15. **Mobile and accessibility:** At 375 px and 768 px viewport widths, confirm the panel fits, text wraps, and controls stay usable. The existing table may scroll horizontally. Use Tab/Shift+Tab to reach Ask AI, textarea, submit, close, and source summaries; use Enter/Space to operate buttons and summaries. Confirm visible focus, a labelled textarea, loading/answer announcements, and error alerts with a screen reader if available.

These manual checks remain outstanding. Synthetic model checks, unit tests, and successful builds do not establish that the authenticated PostgreSQL-to-Ollama RAG workflow passes.
