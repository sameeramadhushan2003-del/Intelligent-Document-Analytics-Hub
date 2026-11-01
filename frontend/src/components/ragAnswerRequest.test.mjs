import test from "node:test";
import assert from "node:assert/strict";
import { fetchRagAnswer, RAG_STATUS_ERRORS } from "./ragAnswerRequest.js";

const params = { documentId: 79, question: "How?", accessToken: "synthetic-session-only" };
const result = { documentId: 79, question: "How?", answer: "Call reception.", model: "qwen2.5:0.5b",
  sources: [{ chunkId: 1, chunkIndex: 0, chunkText: "Call reception.", similarityScore: 0.8 }] };

test("uses the existing proxy, sends only question and forwards cancellation", async () => {
  const controller = new AbortController();
  const value = await fetchRagAnswer({ ...params, signal: controller.signal }, async (url, options) => {
    assert.equal(url, "/api/documents/79/ask");
    assert.equal(options.method, "POST");
    assert.equal(options.headers.Authorization, "Bearer synthetic-session-only");
    assert.deepEqual(JSON.parse(options.body), { question: "How?" });
    assert.equal(options.signal, controller.signal);
    return Response.json(result);
  });
  assert.deepEqual(value, result);
});

test("uses fixed HTTP messages instead of exposing arbitrary server details", async () => {
  for (const status of [400, 401, 403, 404, 409, 500, 502, 503, 504]) {
    await assert.rejects(fetchRagAnswer(params, async () => new Response("private upstream details", { status })),
      { message: RAG_STATUS_ERRORS[status] || "The answer service is unavailable. Please try again shortly." });
  }
});

test("rejects stale document responses and malformed answer/source metadata", async () => {
  for (const data of [null, { ...result, documentId: 80 }, { ...result, sources: null },
    { ...result, sources: [{ ...result.sources[0], similarityScore: "0.8" }] },
    { ...result, sources: [{ ...result.sources[0], chunkIndex: -1 }] }]) {
    await assert.rejects(fetchRagAnswer(params, async () => Response.json(data)), /invalid AI response/);
  }
  await assert.rejects(fetchRagAnswer(params, async () => new Response("not JSON")), /invalid AI response/);
});

test("preserves safe text as text and accepts empty-source fallback", async () => {
  const text = "<script>untrusted</script>";
  const value = await fetchRagAnswer(params, async () => Response.json({ ...result, answer: text, sources: [] }));
  assert.equal(value.answer, text);
  assert.deepEqual(value.sources, []);
});

test("propagates network failure and abort without creating a second request", async () => {
  const controller = new AbortController();
  let calls = 0;
  const pending = fetchRagAnswer({ ...params, signal: controller.signal }, async (url, options) => {
    calls++;
    return new Promise((resolve, reject) => {
      options.signal.addEventListener("abort", () => reject(new DOMException("Cancelled", "AbortError")), { once: true });
    });
  });
  controller.abort();
  await assert.rejects(pending, { name: "AbortError" });
  assert.equal(calls, 1);
  await assert.rejects(fetchRagAnswer(params, async () => { throw new TypeError("Network unavailable"); }), TypeError);
});
