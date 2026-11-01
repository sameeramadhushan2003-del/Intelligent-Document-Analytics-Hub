import test from "node:test";
import assert from "node:assert/strict";
import { embeddingErrorMessage } from "./documentEmbeddings.js";

test("explains that chunks must exist before embeddings", () => {
  assert.equal(
    embeddingErrorMessage(409),
    "Please generate document chunks before generating embeddings."
  );
});

test("gives safe upstream, database, and timeout guidance", () => {
  assert.match(embeddingErrorMessage(502), /invalid response/);
  assert.match(embeddingErrorMessage(503), /unavailable/);
  assert.match(embeddingErrorMessage(504), /Existing embeddings are preserved/);
  assert.doesNotMatch(embeddingErrorMessage(500), /HTTP 500/);
});

test("does not display arbitrary upstream response details", () => {
  assert.doesNotMatch(embeddingErrorMessage(500), /password|stack|document content/i);
});
