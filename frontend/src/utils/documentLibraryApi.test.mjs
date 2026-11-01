import test from "node:test";
import assert from "node:assert/strict";
import { updateDocumentTitle } from "./documentLibraryApi.js";

test("title update sends an authenticated PATCH and returns updated metadata", async () => {
  const updated = {
    id: 42,
    title: "Updated title",
    originalFilename: "original.pdf",
    processingStatus: "EXTRACTED",
  };
  const result = await updateDocumentTitle(
    42,
    "Updated title",
    "synthetic-token",
    async (url, options) => {
      assert.equal(url, "/api/documents/42");
      assert.equal(options.method, "PATCH");
      assert.equal(options.headers.Authorization, "Bearer synthetic-token");
      assert.equal(options.headers["Content-Type"], "application/json");
      assert.deepEqual(JSON.parse(options.body), { title: "Updated title" });
      return new Response(JSON.stringify(updated), { status: 200 });
    },
  );
  assert.deepEqual(result, updated);
});

test("title update exposes safe authentication, validation and ownership errors", async () => {
  for (const [status, message] of [
    [400, "Enter a title with 1 to 255 characters."],
    [401, "Your session has expired. Please sign in again."],
    [404, "This document is no longer available. Refresh your library."],
  ]) {
    await assert.rejects(
      updateDocumentTitle(42, "title", "synthetic-token", async () =>
        new Response(JSON.stringify({ message: "safe server message" }), { status })),
      { message },
    );
  }
});
