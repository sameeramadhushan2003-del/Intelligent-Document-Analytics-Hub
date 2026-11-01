# API reference

Development base URL: `http://localhost:8080`. Vite's `/api` proxy is available from `http://localhost:5173`. This reference reflects the current controller/DTO code, not an OpenAPI-generated specification. All IDs, names, text and token placeholders below are synthetic; substitute only approved test data. Examples are illustrative and were not executed as account/document mutations during documentation work.

## Authentication and errors

Protected requests require `Authorization: Bearer <runtime-access-token>`. Never pass a user ID to select an identity; controllers use the verified JWT subject. Tokens are HS256, issuer `document-analytics-hub`, expire after 900 seconds, and require positive numeric subjects and expiry. Missing/invalid/expired tokens return 401 before document services. Inactive accounts return 403 on document services. Unowned and missing documents return 404 without revealing which case occurred.

JSON requests use `Content-Type: application/json`. Wrong media types can return 415; malformed bodies/types return 400. Unexpected failures can return 500. Error bodies are not one universal schema: RAG returns a fixed `{ "message": "..." }`, registration advice returns messages, Spring Security may return an empty 401 body/Bearer challenge, and default MVC errors omit internal details. Clients should primarily inspect status, not expect raw Java exception text.

For safe PowerShell GET/JSON examples, `$privateHeaders` means an in-memory header map already established by your approved test client; do not print/export it. Do not type real tokens into command history or share HAR captures. Use the UI for authorized login/uploads when possible.

## Endpoint inventory

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET | `/api/health` | Public | Web application health |
| DELETE | `/api/documents/{documentId}` | Authenticated | Delete an owned document |
| POST | `/api/auth/register` | Public | Register an account |
| POST | `/api/auth/login` | Public | Obtain access token |
| GET | `/api/auth/me` | Bearer | Account details |
| POST | `/api/documents/upload` | Bearer | Private multipart upload |
| GET | `/api/documents` | Bearer | Own document metadata list |
| GET | `/api/documents/{documentId}` | Bearer | One owned document's metadata |
| POST | `/api/documents/{documentId}/process` | Bearer | Extract/store text |
| POST | `/api/documents/{documentId}/chunks/generate` | Bearer | Generate/store chunks |
| POST | `/api/documents/{documentId}/embeddings/generate` | Bearer | Generate/upsert embeddings |
| POST | `/api/documents/{documentId}/search` | Bearer | Semantic search |
| POST | `/api/documents/{documentId}/ask` | Bearer | RAG answer and context |

There are no implemented download, refresh-token, public-document or streaming endpoints.

## Health

`GET /api/health` → 200:

```json
{"status":"UP","application":"Intelligent Document Analytics Hub"}
```

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/api/health'
```

This is not a comprehensive readiness check for models or stored embeddings.

## Registration

`POST /api/auth/register` (`RegistrationController`), public JSON:

```json
{"fullName":"Synthetic Student","email":"student@example.invalid","password":"synthetic-fixture-only"}
```

201 response:

```json
{"id":7,"fullName":"Synthetic Student","email":"student@example.invalid","role":"USER","createdAt":"2026-11-01T00:00:00Z"}
```

Full name: nonblank, max 150 characters. Email: nonblank/valid, max 320; service trims and lowercases it. Password: 8–72 characters and at most 72 UTF-8 bytes; no password hash is returned. Relevant errors: 400 invalid input, 409 duplicate email, 500 unexpected storage failure. This endpoint creates a user: do not run it against real accounts merely to verify documentation.

## Login and current account

`POST /api/auth/login` (`AuthController`), public JSON:

```json
{"email":"student@example.invalid","password":"synthetic-fixture-only"}
```

200 response structure:

```json
{"accessToken":"<issued-at-runtime-not-a-real-token>","tokenType":"Bearer","expiresIn":900,"userId":7,"fullName":"Synthetic Student","email":"student@example.invalid","role":"USER"}
```

Login validates nonblank email/password, email syntax and bounded lengths; password must fit BCrypt's 72 UTF-8-byte limit. Relevant errors: 400 validation, 401 invalid credentials/inactive account, 500 unexpected failure. Do not log or copy the returned real token. React stores it in tab-scoped `sessionStorage` and verifies it through `/api/auth/me` after a reload; it expires after 900 seconds. There is no refresh-token endpoint.

`GET /api/auth/me` requires Bearer authentication. It has no body and returns 200:

```json
{"id":7,"fullName":"Synthetic Student","email":"student@example.invalid","role":"USER"}
```

Relevant errors: 401 invalid/expired JWT, missing/inactive account. Safe request form: `Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/me' -Headers $privateHeaders`.

## Upload

`POST /api/documents/upload` (`DocumentUploadController`) accepts `multipart/form-data` with **file** part named `file`. The owner is taken from the JWT, not a form field.

Illustrative request:

```http
POST /api/documents/upload HTTP/1.1
Authorization: Bearer <runtime-access-token>
Content-Type: multipart/form-data; boundary=synthetic-boundary

--synthetic-boundary
Content-Disposition: form-data; name="file"; filename="appointments.txt"
Content-Type: text/plain

Patients book appointments by calling reception.
--synthetic-boundary--
```

201 response:

```json
{"documentId":42,"message":"Document uploaded successfully.","processingStatus":"UPLOADED"}
```

Accepted extensions: PDF, DOCX, TXT. Validation checks PDF signature, required DOCX entries/no macro entry, and UTF-8 text; it is not antivirus scanning or full PDF parsing. File limit: 10 MB; multipart request limit: 12 MB. Relevant errors: 400 empty/invalid/unsupported file, 401 invalid account/authentication, 403 inactive account, 413 too large, 415 wrong media type, 500 storage/persistence failure. UUID storage key and SHA-256 are internal. Use an approved synthetic file through the UI; do not upload private documents for public examples.

## Document metadata

`GET /api/documents` (`DocumentLibraryController`) returns a 200 array of the current user's metadata; no documents is `[]`.

`GET /api/documents/42` returns a single 200 object of the same shape:

```json
{"id":42,"title":"appointments.txt","originalFilename":"appointments.txt","contentType":"text/plain","fileSizeBytes":47,"processingStatus":"EXTRACTED","uploadedAt":"2026-11-01T00:00:00Z"}
```

Relevant errors: 401 invalid/missing account or JWT, 403 inactive account, 404 missing/unowned single document. Responses do not include storage keys, extracted text, full binary content, owner credentials or password hashes.

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/api/documents' -Headers $privateHeaders
# Use an actual approved owned fixture ID instead of this illustrative 42.
Invoke-RestMethod -Uri 'http://localhost:8080/api/documents/42' -Headers $privateHeaders
```

## Delete a document

`DELETE /api/documents/42` (`DocumentLibraryController`) requires a Bearer token and the authenticated user must own the document. The database deletion includes dependent extraction, chunk, and embedding records through the configured foreign-key relationships. The original file is staged before the database transaction and removed after commit; if the transaction fails, the service attempts to restore the staged file.

- `204 No Content`: document metadata was deleted and file cleanup completed. The response has no JSON body.
- `202 Accepted`: metadata was deleted, but post-commit filesystem cleanup is pending. The response contains `filesystemCleanupPending: true` and a message.
- `401 Unauthorized`: authentication is missing or invalid.
- `403 Forbidden`: the account is inactive or access is forbidden.
- `404 Not Found`: the document is missing or is not owned by the caller.
- `500 Internal Server Error`: deletion failed before commit.

The database commit and filesystem cleanup cannot be one atomic operation. A `202` means the document is gone from the library, with file cleanup still requiring attention. This operation is destructive; use only an approved disposable document when exercising it. A repeated request after successful deletion returns the safe missing-document response.

```powershell
# Use only an approved disposable document ID.
Invoke-RestMethod -Method Delete -Uri 'http://localhost:8080/api/documents/42' -Headers $privateHeaders
```

## Extract text

`POST /api/documents/42/process` (`DocumentProcessingController`), Bearer, **no body**.

200 response:

```json
{"documentId":42,"message":"Document text extracted successfully.","characterCount":47,"extractedAt":"2026-11-01T00:00:00Z"}
```

Ownership precedes reading/parsing the private file. The extraction is saved and status set to `EXTRACTED`. Relevant errors: 400 invalid ID/input, 401 authentication, 403 inactive account, 404 missing/unowned document, 422 unreadable/no usable extraction or I/O failure, 500 unexpected parsing/persistence failure. Reprocessing updates derived data.

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/documents/42/process' -Headers $privateHeaders
```

## Generate chunks

`POST /api/documents/42/chunks/generate` (`DocumentChunkController`), Bearer, **no body**.

200 response:

```json
{"documentId":42,"chunkCount":1,"message":"Document chunks generated successfully."}
```

Uses saved extraction; maximum chunk size 1000 Java characters, overlap 150. Relevant errors: 400 invalid input, 401/403 authentication/account, 404 missing/unowned document, 409 extraction required, 500 persistence failure. Regeneration may replace derived chunks/embeddings; it is not a read-only query.

## Generate embeddings

`POST /api/documents/42/embeddings/generate` (`DocumentEmbeddingController`), Bearer, **no body**.

200 response:

```json
{"documentId":42,"embeddingCount":1,"message":"Document embeddings generated successfully."}
```

Generates 384-dimensional all-minilm vectors sequentially and upserts by chunk/model. Relevant errors: 400 invalid IDs/input, 401/403 authentication/account, 404 missing/unowned document, 409 chunks required, 500 unexpected inference/persistence failures. Non-RAG model errors do not necessarily share the Ask AI 502/503/504 contract. Regeneration writes derived data.

## Semantic search

`POST /api/documents/42/search` (`DocumentSemanticSearchController`), Bearer JSON:

```json
{"query":"How do patients book appointments?","limit":5}
```

Query is nonblank, trimmed, max 10000 Java characters. `limit` is optional, defaults to 5 and must be 1–20. 200 response:

```json
{"documentId":42,"query":"How do patients book appointments?","resultCount":1,"results":[{"chunkId":101,"documentId":42,"chunkIndex":0,"chunkText":"Patients book appointments by calling reception.","similarityScore":0.695}]}
```

Scores/IDs here are illustrative, not real document data. Similarity is `1 - cosine distance`, not a confidence probability. Ordering uses distance ascending, then chunk index and ID; search does not apply RAG's 0.20 threshold. Relevant errors: 400 invalid query/limit/body/ID, 401/403 authentication/account, 404 missing/unowned document, 409 no searchable embeddings, 500 unexpected inference/data-access error.

```powershell
$queryBody = @{ query = 'How do patients book appointments?'; limit = 5 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/documents/42/search' -Headers $privateHeaders -ContentType 'application/json' -Body $queryBody
```

## Ask AI

`POST /api/documents/42/ask` (`DocumentRagAnswerController`), Bearer JSON:

```json
{"question":"How do patients book appointments?"}
```

Question must be nonblank, trimmed and at most 1000 Java characters. There is no accepted ownership-selection parameter. 200 response:

```json
{"documentId":42,"question":"How do patients book appointments?","answer":"Patients book appointments by calling reception.","model":"qwen2.5:0.5b","sources":[{"chunkId":101,"chunkIndex":0,"chunkText":"Patients book appointments by calling reception.","similarityScore":0.695}]}
```

Source labels follow supplied order and actual metadata, not model-generated citations. Returned chunks are original text supplied to Qwen. Empty/weak/oversized usable context returns the fixed insufficient-information answer with `sources: []`, without chat inference. If Qwen rejects the question after receiving context, that supplied context can still be returned alongside the fallback.

Relevant statuses:

| Status | Meaning |
| --- | --- |
| 200 | Grounded passage or insufficient-information fallback |
| 400 | Invalid document ID, question or malformed request |
| 401 | Missing, invalid or expired JWT/account |
| 403 | Account inactive or access forbidden |
| 404 | Document missing or not owned |
| 409 | No searchable embeddings |
| 502 | Failed/malformed/empty model response |
| 503 | AI connection/embedding service unavailable |
| 504 | Chat inference socket timeout |
| 500 | Unexpected internal failure |

RAG status messages are sanitized and should not expose stack traces, SQL, upstream bodies or secrets. Unsupported information is generally a 200 fallback, not a validation error.

```powershell
$questionBody = @{ question = 'How do patients book appointments?' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/documents/42/ask' -Headers $privateHeaders -ContentType 'application/json' -Body $questionBody
```

Run read-only requests only against approved test documents. Never include real JWTs, private text, credentials or Authorization headers in reports/screenshots. See [final verification](final-verification.md) for what actually ran and what remains manual.
