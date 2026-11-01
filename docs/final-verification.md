# Step 34.14 — security, performance and integration verification

Verified on 2026-11-01 (Asia/Colombo). This report distinguishes automated checks from live checks and manual work still required.

## Final submission-preparation rerun

On 2026-11-01, the backend compiled successfully and the selected database-independent suite discovered 92 tests: **90 passed, 0 failed/errors, 2 skipped**. The skipped tests are the opt-in Ollama integration tests; no live Ollama request or database integration test was run in this rerun. The selected tests use synthetic fixtures, temporary files, or mocked data access and do not modify an existing application database.

The frontend `npm.cmd run build`, `npm.cmd run lint`, and `npm.cmd test` commands passed; the Node test runner reported **32 passed, 0 failed**. Local Markdown links in the README and seven documentation pages resolved. `git diff --check` passed after documentation whitespace cleanup. No authenticated browser workflow or fresh-install PostgreSQL provisioning is claimed.

The final-preparation scan checked current non-generated repository files and Git-reachable text blobs for sensitive-looking filenames and common credential/token patterns. It found no confirmed credential or private-document content; candidate matches were source identifiers and synthetic examples. Environment configuration uses required variables without committed credential defaults. This is a targeted scan, not a guarantee against every possible secret format.

## Reproducing the selected checks (Step 34.15 documentation handover)

The Step 34.14 findings and results below are preserved. Documentation was prepared from the actual controllers, configuration, migrations and tests. No latest authenticated browser pass has been added or inferred.

Step 34.15 rerun on 2026-11-01: separate Maven compilation passed; the selected suite with the live chat flag discovered 74 tests, **73 passed, 0 failed/errors, 1 skipped**. The sequential synthetic live Qwen check passed; the opt-in live embedding JUnit test remained skipped. All **5 frontend request tests**, production build, relevant-file ESLint, relative documentation link checks and `git diff --check` passed. No authenticated browser test or fresh-install database provisioning was performed.

From `backend`, this suite excludes tests that start the live database application context or write existing database records:

```powershell
$selectedTests = @(
    'AuthInputValidationTest',
    'DocumentSecurityIntegrationTest',
    'PrivateFileStorageServiceTest',
    'DocumentDeletionServiceTest',
    'DocumentEmbeddingPersistenceServiceTest',
    'DocumentChunkEmbeddingRepositoryTest',
    'DocumentSemanticSearchRepositoryTest',
    'DocumentRagAnswerControllerTest',
    'DocumentRagAnswerServiceTest',
    'OllamaChatServiceTest',
    'DocumentProcessingServiceTest',
    'DocumentChunkingServiceTest',
    'DocumentChunkPersistenceServiceTest',
    'DocumentTextExtractionServiceTest',
    'OllamaEmbeddingServiceTest'
) -join ','
.\mvnw.cmd -B compile
.\mvnw.cmd -B "-Dtest=$selectedTests" test
```

This uses synthetic fixtures, temporary files and mock data access. With neither live-model opt-in enabled, both model integration tests are skipped. To also run the chat test **sequentially** on local synthetic data, ensure Ollama and `qwen2.5:0.5b` are available and use:

```powershell
.\mvnw.cmd -B "-Dtest=$selectedTests" '-Drun.ollama.integration=true' test
```

The existing live embedding test is separately enabled by `RUN_OLLAMA_INTEGRATION_TEST=true` in its launching process; this is not required for the selected default run. Avoid enabling unrelated external tests or invoking concurrent model work on the 8 GB laptop. Use `-o` only with cached dependencies; an explicit `-Dmaven.repo.local=...` and cached executable can be used as described in [setup](setup-guide.md).

From `frontend`:

```powershell
npm.cmd run build
npm.cmd run lint
npm.cmd test
```

Relevant-file lint is the verified scope; do not interpret it as a claim that unrelated legacy files pass repository-wide `npm run lint`. From the project root run `git diff --check` and `git status --short`. Latest browser steps remain at the end of this report.

### Documentation/Git safety review

At the earlier Step 34.15 handover, the README, setup guide, API reference, architecture diagrams and submission checklist were documented without application-code changes. Relative documentation links were checked locally; GitHub Mermaid rendering and fresh-clone setup still require manual preview/testing.

That earlier targeted review found no dependency/build folders, private upload directories, PDF/DOCX uploads, key files or database dumps in its checked categories. Current required secrets were environment placeholders with no literal defaults. The broader current submission-preparation scan is summarized above; neither scan guarantees every possible secret format is absent. If a real secret is discovered, rotate/revoke it privately and plan coordinated history cleanup; do not reset history automatically.

Ignore rules cover build output/dependencies, `.env` variants, private upload directories, logs, dumps, HAR captures and JVM diagnostics. At that earlier handover, implementation files remained untracked and no commit or push had been performed. This is a historical note; consult the repository's current Git history for the final submission state.

## Files changed in this step

- `.gitignore`
- `backend/src/main/java/com/documentanalytics/backend/config/SecurityConfig.java`
- `backend/src/main/java/com/documentanalytics/backend/dto/LoginRequest.java`
- `backend/src/main/java/com/documentanalytics/backend/service/AuthService.java`
- `backend/src/main/java/com/documentanalytics/backend/service/OllamaChatService.java`
- `backend/src/main/resources/application.properties` (error settings only; existing credential placeholders preserved)
- `backend/src/test/java/com/documentanalytics/backend/AuthInputValidationTest.java` (new)
- `backend/src/test/java/com/documentanalytics/backend/DocumentEmbeddingPersistenceServiceTest.java` (new)
- `backend/src/test/java/com/documentanalytics/backend/DocumentSecurityIntegrationTest.java` (new)
- `backend/src/test/java/com/documentanalytics/backend/PrivateFileStorageServiceTest.java` (new)
- `backend/src/test/java/com/documentanalytics/backend/service/OllamaChatServiceTest.java`
- `frontend/src/components/DocumentRagAnswer.jsx`
- `frontend/src/components/ragAnswerRequest.js` (new)
- `frontend/src/components/ragAnswerRequest.test.mjs` (new)
- `docs/rag-reliability.md`
- `docs/final-verification.md` (new)

Other changes already in the working tree from prior steps were preserved.

## Security findings and targeted corrections

- The real JWT decoder already validated HS256 signatures, issuer, and timestamps. Some controllers directly parsed JWT subjects, so a correctly signed token with an invalid subject could cause an internal parsing error. The decoder now also requires an expiry and a positive numeric subject before any controller runs. Existing 15-minute tokens remain compatible; Spring Security's existing clock-skew allowance remains unchanged.
- Added an integration fixture using the actual Spring Security filter chain, encoder/decoder, document controllers and ownership services. It uses a randomly generated in-memory test key, mock repositories and mock file/model services. It never reads production signing keys or mints tokens for the live application. Across library, read, upload, extraction, chunking, embeddings, semantic search and Ask AI routes, absent/malformed/expired/wrong-issuer/invalid-subject tokens return 401. Forged signatures and missing expiry are also rejected. Ownership-filtered requests return 404; inactive accounts return 403. Verification confirms that denial occurs before private file work, extraction, chunking, embedding storage or Ollama calls. Request-body user IDs do not override the JWT subject.
- Login input now has bounded email/password lengths and rejects passwords exceeding BCrypt's 72 UTF-8-byte limit before repository lookup or hashing. Registration already enforced length, email validity and the BCrypt byte limit. Authentication tests use synthetic input and mocks, not real accounts.
- Default HTTP errors explicitly suppress messages, binding details, stack traces and exception names. The existing controller-scoped RAG advice continues to provide safe fixed error messages. No custom application logging of passwords, signing keys or access tokens was found in the reviewed source. Review production log access and retention independently; this is not a history-wide secret audit.
- Current application password and JWT secret settings are required environment placeholders without literal fallback secrets. Their values were not displayed or changed. Added `.env.*` to ignore rules, preserving `.env.example` as a possible documentation template. Previously committed secrets would still require independent rotation/history handling.
- Private storage uses generated UUID keys outside the web root, strips client path components, caps uploads at 10 MB both by reported size and streamed bytes, checks PDF signatures/DOCX structure/UTF-8 text, and cleans up temporary files. Tests use only JUnit temporary directories. Extraction validates canonical UUID keys and refuses symlink files. Parser resource limits and ZIP-bomb defenses remain relevant; these checks are not antivirus scanning or complete PDF validation.
- SQL values for ownership, search, vectors, models and limits remain parameterized. No schema or index changes were made. React continues to render answers and source text through escaped text interpolation, with no `dangerouslySetInnerHTML`.

## Grounding and false negatives

Standalone paraphrases cannot be reliably verified with a substring check. Accepting them using word overlap or another unconstrained small-model judgment would weaken grounding. The targeted improvement recovers a quoted passage from a response containing commentary/paraphrase **only if that passage actually occurs in the supplied context**. It returns the quote alone and discards the surrounding unverified claims. No extra inference request is introduced. Smart quotes are supported for this recovery; fabricated quotes and unsupported standalone paraphrases still produce the fixed fallback. Tests cover both recovered evidence and rejected unsupported text.

This improves responses such as `Contact the front desk. Evidence: "Patients book appointments by calling reception."` without treating `Contact the front desk.` alone as verified. Exact matching is still not proof of relevance, entailment or truth. Arbitrary prompt injection is not proven impossible. Source labels/IDs/scores still come only from retrieved records and are never constructed from generated citations. The JSON contract remains `documentId`, `question`, `answer`, `model`, `sources`.

## Performance review

- Retained all-minilm (384 dimensions) and qwen2.5:0.5b. No dependencies, larger models, background workers or load tests were added.
- Qwen remains bounded to 4096 context tokens, 160 output tokens, temperature 0, `keep_alive: 0`, 10-second connect and 180-second read timeouts. The existing combined UTF-8 message budget, source filtering and maximum three chunks remain. UTF-8 bytes are a conservative heuristic rather than exact tokenizer accounting. On 8 GB RAM, keep model requests sequential; browser cancellation does not guarantee immediate cancellation of server inference.
- Quote recovery reuses one normalized context and performs no extra model call. Frontend duplicate prevention still acquires a ref lock before awaiting the request; panel close/document/session changes abort and remount state, and responses are checked against the selected document.
- Frontend HTTP handling was extracted to a small dependency-free helper for actual request/error/cancellation tests. It also rejects invalid score/index metadata. Raw server error text is never surfaced.
- Migration review found the document owner/upload index, `(document_id, chunk_index)` uniqueness and `(chunk_id, embedding_model)` uniqueness, supporting owner-filtered and per-document retrieval. No ANN vector index is defined in the reviewed migrations. Exact filtered search is appropriate for the present small-document workflow; no runtime database EXPLAIN or production-scale performance claim is made. Measure larger workloads before adding an index/migration.

## Automated validation

- Backend compilation passed through Maven's compile phase.
- Selected backend suite: **74 discovered, 73 passed, 0 failures/errors, 1 skipped**. Covers JWT filtering, ownership denial before private work, auth input limits, synthetic upload validation, extraction, chunk generation/persistence, embedding storage sequencing, cosine-distance conversion, RAG context/source ordering, prompt handling, unsupported claims, model failures/timeouts, and safe errors.
- The optional live Qwen test actually ran sequentially on synthetic appointment/astronomy inputs and passed grounded-answer and missing-information checks. The existing opt-in live embedding JUnit test was skipped.
- Database-writing repository/registration/service tests and the default full-database application-context tests were **not run** against the existing database, to preserve user data and avoid migrations. The security integration fixture exercises real web/security wiring with mock data access; it is not a live PostgreSQL integration test.
- Frontend Node tests: **5 passed**. Covers method/body/auth-header construction with synthetic placeholders, AbortSignal forwarding, fixed HTTP errors, malformed/stale responses, source score/index validation, safe text preservation, empty-source fallback, network failure and cancellation. Run from `frontend`: `node --test src/components/ragAnswerRequest.test.mjs`.
- Frontend production build and ESLint for the component, HTTP helper, helper tests and Documents page passed.
- `git diff --check` passed; working-tree status was reviewed. Existing uncommitted work from earlier steps remains uncommitted.

These frontend helper tests do not mount React or establish that rapid-click, switching, closing, screen-reader or mobile interactions pass in a browser. Those behaviors were reviewed in code and still need the manual steps below.

## Live integration observed

The existing running backend health endpoint, Ollama tags endpoint and Vite HTML endpoint each returned HTTP 200. Live unauthenticated Ask AI and Ask AI with a malformed synthetic bearer returned 401. Ask AI through Vite's `/api` proxy also returned 401 without authentication, confirming the proxy reaches Spring Security.

No real password or JWT was requested, displayed, logged or stored for these checks. No real user account or uploaded document was changed. The running backend was not restarted; its exact loaded revision is not established. Therefore live endpoint checks alone do not certify that it has loaded this step's changes. Current code was exercised by the automated security integration fixture and direct optional live model test. Authenticated end-to-end verification of the rebuilt application remains manual.

## Manual checks for the latest version and demo readiness

1. Restart Spring Boot from your trusted existing launch configuration so it loads the rebuilt classes, without changing credentials, accounts or schema. Keep migrations disabled during verification if schema changes must be avoided. Keep the existing frontend on `http://localhost:5173`.
2. Sign in through the UI using an approved existing test account; do not paste credentials/tokens into chat or export HAR files. Confirm My Documents loads and the existing processing/search actions remain available.
3. Select an approved, owned test document from the signed-in account's library only if it already has embeddings. Ask a question answered by its text; expect a concise quoted passage, the model name, expandable original chunks and finite similarity scores. Check the answer against the source privately.
4. Ask a clearly unrelated question; expect insufficient information. Also test a question where the model might paraphrase and confirm it either returns verified quoted evidence or conservatively declines. Check source labels and text remain accurate.
5. Double-click Ask AI rapidly and confirm one network request. Submit again after completion. Switch to another owned document while generating and confirm clean question/answer state with no stale result. Close and reopen during generation; confirm cancellation and restored focus. Wait for inference to finish before submitting another model request.
6. In DevTools, take the browser offline after opening the panel, submit, and confirm the connection error and usable retry controls. Restore the network. Test expired-session sign-in guidance after normal expiry; do not fabricate real tokens. Use approved fixtures or request editing for 400/404/409 checks without sharing Authorization headers. Exact API/manual instructions are also in `live-rag-verification.md`.
7. Only with a second approved existing test account and known test document, verify that the first account cannot read, process, chunk, embed, search or ask about it. Do not create accounts or enumerate private IDs for this check.
8. At 375 px and 768 px, check wrapping and controls. Use Tab, Shift+Tab, Enter and Space for the Ask action, textarea, submit, close and source summaries. Confirm visible focus, status/error announcements and no executable HTML from sources.

Ready to finalize technical documentation now. **University demonstration readiness is conditional on completing the restarted application's authenticated/browser checklist above.** Earlier browser results do not validate these latest changes. This review does not constitute a production penetration test; global rate/concurrency limiting, malware scanning, representative relevance calibration and larger-scale performance remain outside the verified scope.
