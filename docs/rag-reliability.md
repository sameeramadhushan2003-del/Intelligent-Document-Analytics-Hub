# RAG reliability — Step 34.13

## Architecture and API

`POST /api/documents/{documentId}/ask` uses the JWT subject as the authenticated user ID. The existing semantic-search service checks ownership before calling all-minilm or the repository. Missing embeddings remain HTTP 409. The response fields remain `documentId`, `question`, `answer`, `model`, `sources`; each source retains its actual chunk ID, chunk index, original text, and cosine similarity. There are no schema changes or new dependencies.

The user reports that the previous six browser checks passed. Those are user-reported results, not new automated browser results for these changes. The Step 34.12 report is a historical record of the earlier environment failure.

## Retrieval and threshold evaluation

pgvector's `<=>` returns cosine **distance**; similarity is `1 - distance`, not a percentage or probability. SQL NULL distances now map to NaN rather than an apparent perfect 1.0 match. Results order by distance, then chunk index, then chunk ID. RAG rejects nonfinite/out-of-range scores, foreign-document metadata, missing IDs/indices, blank text, and duplicate chunk IDs. RAG sorts descending similarity, breaking ties by index and ID.

The existing top-three retrieval and 0.20 threshold remain. A live all-minilm synthetic evaluation on 2026-11-01 measured:

| Question | Appointment/hours chunk | Unrelated invoice chunk |
| --- | ---: | ---: |
| Appointment booking | 0.695 | 0.187 |
| Clinic hours | 0.767 | 0.078 |
| Neptune orbital period | 0.105 | 0.087 |
| Baking a cake | 0.028 | 0.030 |

For this small fixture, 0.20 retained the two relevant matches and rejected the unrelated matches. This is insufficient evidence to declare it universally calibrated or to raise it safely. Before changing it, evaluate labelled relevant/irrelevant and difficult near-topic questions on representative approved test documents; compare false acceptance and false rejection. Tests using mocked scores check decision logic, not embedding quality.

## Context budget and answer policy

The 1000-character question limit and original chunk contents remain intact. Context selection includes complete original chunks only. Oversized candidates are skipped, allowing smaller later candidates to fit; nothing is silently truncated. If no usable context fits or passes relevance filtering, the service returns `I could not find that information in the document.` without running Qwen. Extremely long/multibyte questions or chunks may leave no usable context and cause this conservative fallback.

Qwen remains `qwen2.5:0.5b`, temperature 0, at most 160 generated tokens, with `keep_alive: 0`. The context window is modestly increased from 2048 to 4096 so a conservative combined **3600 UTF-8 byte** budget can accommodate existing 1000-character chunks. The system message, question, user-message instructions, source headers and original text all count against this budget, leaving room for output and chat-template overhead. UTF-8 bytes are a conservative heuristic rather than an exact tokenizer measurement. No parallel inference or larger model is introduced.

A live synthetic check showed that Qwen returned a correct natural-language paraphrase, but the backend explicitly required a source substring or quote and replaced the answer with the fallback. The prompt now asks for a concise, faithful natural-language answer. Verification accepts normalized source matches, verified quotations, or a paraphrase only when at least half of its distinct content terms overlap the retrieved passages (with a narrow canonicalization for “responsible/keeping” versus “maintains”). The accepted answer remains the model's answer; the source chunks and their metadata remain the application's citations. Unsupported answers, low-overlap paraphrases, and invented citation suffixes still fall back. Term overlap is a conservative lexical check, not a semantic entailment proof, so adversarial or unusually worded claims remain a limitation. Live integration tests cover the records question, a three-milestone summary, an unrelated question, and source metadata.

Document data and questions are explicitly treated as untrusted in the system prompt. Prompt-injection content stays in the original source data; it is not silently removed or promoted to a system instruction. Unit tests verify message separation, fixed instructions, and content preservation. They do **not** prove that arbitrary malicious documents cannot influence the model. Exact matching reduces unsupported text but does not prove relevance, entailment, completeness, or truth; a misleading passage already in the document can still be quoted.

## Source presentation

The expandable React sources are labelled **Document context supplied to AI**. Application-generated `Source 1`, `Source 2`, etc. follow the actual order supplied to Qwen and include real chunk indices and scores. They are context disclosures, not verified sentence-level citations. No generated citation labels are parsed or trusted to construct source metadata. If Qwen declines after receiving context, those sources remain visible as the context it received.

## Errors and privacy

The controller-scoped exception handler returns fixed messages without stack traces, upstream bodies, SQL, tokens or credentials. It preserves 400/401/403/404/409 statuses, maps unavailable embedding services to 503, chat connection failure to 503, chat timeout to 504, and malformed/failed model responses to 502. Unexpected failures return a generic 500. JWT verification still runs through Spring Security before the controller. Authentication was not bypassed, and accounts were not changed.

The frontend has specific 502/503/504 messages, retains network-failure handling, and renders text through React's escaping. It does not display raw upstream exception messages or JWTs.

## Tests and live checks

Synthetic unit tests cover relevant, unrelated, weak, blank, duplicate, foreign, invalid-score, multi-chunk and oversized context; source metadata/order; default-size chunks; multibyte budgets; ownership-before-inference; missing embeddings; prompt-injection data; HTTP/model failures and timeouts; malformed/empty responses; unsupported generated claims; and invented citations. Repository tests check distance conversion and SQL NULL handling using a mock ResultSet, not a live pgvector database.

The optional chat integration test runs sequentially against local Qwen with `-Drun.ollama.integration=true`. It checks appointment answering and the missing-information fallback using synthetic text. The existing live embedding JUnit test remains opt-in through `RUN_OLLAMA_INTEGRATION_TEST`; the separate live embedding threshold evaluation above actually ran. Ordinary regression tests require neither Ollama nor a database.

Final verification: backend Maven compilation passed; selected regression suite discovered 60 tests, with **59 passed, 0 failed/errors, 1 skipped**. The optional live Qwen test actually ran and passed both the appointment and unrelated-question assertions sequentially. The skipped test is the existing opt-in live embedding JUnit test. Frontend production build, ESLint for `DocumentRagAnswer.jsx`/`Documents.jsx`, and `git diff --check` passed.

No authenticated live document request or new browser test is claimed for this step. Existing manual instructions remain in `live-rag-verification.md`; recheck representative answers, fallback behavior, source expansion and new error messages after restarting the backend with these changes.
