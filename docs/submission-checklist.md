# Submission and demonstration checklist

This is a project-specific preparation aid, not a university rubric. No institution-specific requirements, deadline, license or deployment target have been supplied. Do not mark pending items complete based only on earlier browser checks.

## Implemented and recorded

- [x] Authentication, private uploads/extraction/chunking/embeddings, semantic search and Ask AI implemented.
- [x] Latest recorded selected backend suite: 73 passed, 0 failed, 1 skipped.
- [x] Five frontend HTTP/request tests passed.
- [x] Backend compilation, frontend production build and relevant-file ESLint passed.
- [x] JWT/security integration and grounding/failure tests recorded.
- [x] Sequential live synthetic model checks recorded separately from authenticated end-to-end checks.
- [x] README, architecture/sequence diagrams, API reference and Windows setup guide created from source.
- [x] Existing Flyway/database setup and private environment requirements documented.
- [x] Repository safety review and ignore rules reviewed; no local data deleted or Git history rewritten.

## Latest application/browser regression — pending

- [ ] Restart Spring Boot with trusted private configuration to load the latest compiled code; do not reset schema/accounts.
- [ ] Confirm PostgreSQL/pgvector, Ollama models, backend health and frontend proxy.
- [ ] Login using an approved existing test account; confirm current account and document library.
- [ ] On an approved owned embedded fixture, confirm relevant answer, model, original source metadata and expandable text.
- [ ] Confirm unrelated-question fallback and conservative paraphrase/verified-quote behavior.
- [ ] Confirm rapid clicks send one request; switching/closing during generation does not display stale results.
- [ ] Confirm 401/403/404/409 handling and offline retry using safe fixtures; no private errors in the interface.
- [ ] If two approved existing test accounts are available, verify cross-owner denial without creating accounts or enumerating documents.
- [ ] Check mobile widths, keyboard operation/focus and accessible status/error feedback.
- [ ] Recheck existing upload/process/chunk/embed/search actions on an approved synthetic fixture only; these actions write data.

Use [final verification](final-verification.md) and [the manual workflow](live-rag-verification.md) for exact steps. A passing build/helper test is not a mounted React/browser test.

## Screenshots to capture from real runs

- [ ] Home/dashboard showing the actual application.
- [ ] Approved test document library without private names.
- [ ] Synthetic upload and processing result.
- [ ] Semantic search with synthetic chunk text and similarity scores.
- [ ] Ask AI relevant answer, model and expanded synthetic sources.
- [ ] Insufficient-information/error state.
- [ ] Mobile layout if needed for the demonstration.
- [ ] Test/build summaries with no tokens, credentials or private paths/text.

Screenshots have **not** been fabricated or captured by this documentation step. Avoid browser Network headers, real email addresses, private document names, shell environment dumps and full database views. Use explicit synthetic fixtures and remove unrelated personal details before publication.

## Repository hygiene for future changes

- Review `git status --short` and diffs before any later commit.
- Keep dependencies, generated builds, logs, uploads, credentials, HAR files and database dumps out of Git.
- Inspect configuration and staged content privately; never print credential values in reports.
- Keep migrations, Maven Wrapper files, `pom.xml`, frontend lockfile, source, tests, and public UI assets together.
- Preview Mermaid diagrams and relative links in GitHub after documentation changes.
- Test setup in an isolated fresh environment before making fresh-install claims.
- Confirm any license, citation, report, or repository visibility requirements with the project owner rather than inventing them.

Ignore rules do not remove already-tracked files or erase history. The final-preparation scan checked current non-generated files and Git-reachable text blobs for sensitive-looking filenames and common credential/token patterns; no confirmed credential or private-document content was identified. Candidate matches were source identifiers and synthetic examples. This targeted scan is not a guarantee against every possible secret format. If a real secret is discovered later, rotate/revoke it privately and coordinate any history cleanup; do not rewrite history automatically.

## Demonstration preparation

- [ ] Start services in the documented order and reserve the 8 GB laptop for sequential inference.
- [ ] Prepare an approved synthetic appointment/clinic fixture and known answered/unanswered questions.
- [ ] Keep documents already processed/embedded for the read-only portion of the demo.
- [ ] Explain ownership-before-inference, cosine scores, conservative quoting and why sources are context disclosures rather than verified citations.
- [ ] Explain limitations honestly: no OCR/antivirus/refresh tokens, possible grounding false negatives, prompt-injection risk, no global inference admission control and no production deployment claim.
- [ ] Prepare a fallback walkthrough using real, previously captured synthetic screenshots if local services fail; label it as recorded rather than live.

**Readiness:** technical documentation and automated checks are recorded. Fresh-install provisioning and the latest authenticated end-to-end browser workflow remain unverified; any demonstration or production-readiness claim should disclose that limitation. University demonstration readiness also depends on requirements supplied by the project owner.
