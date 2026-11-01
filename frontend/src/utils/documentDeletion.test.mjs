import test from "node:test";
import assert from "node:assert/strict";
import {
  deleteDocument,
  removeDeletedDocument,
  runDeleteOnce,
} from "./documentDeletion.js";
import { refreshDocuments } from "./documentLibraryApi.js";

test("sends authenticated DELETE through the same-origin API proxy", async () => {
  const result = await deleteDocument(19, "synthetic-token", async (url, options) => {
    assert.equal(url, "/api/documents/19");
    assert.equal(options.method, "DELETE");
    assert.equal(options.headers.Authorization, "Bearer synthetic-token");
    return new Response(null, { status: 204 });
  });
  assert.deepEqual(result, { filesystemCleanupPending: false });
});

test("reports committed deletion when quarantined-file cleanup remains pending", async () => {
  const result = await deleteDocument(19, "synthetic-token", async () =>
    new Response(null, { status: 202 })
  );
  assert.deepEqual(result, { filesystemCleanupPending: true });
});

test("accepts a successful 200 response without requiring a response body", async () => {
  const result = await deleteDocument(19, "synthetic-token", async () =>
    new Response(null, { status: 200 })
  );
  assert.deepEqual(result, { filesystemCleanupPending: false });
});

test("keeps other library entries when removing a deleted document", () => {
  const documents = [{ id: 19 }, { id: 20 }, { id: 21 }];
  assert.deepEqual(removeDeletedDocument(documents, 20), [{ id: 19 }, { id: 21 }]);
  assert.equal(documents.length, 3);
});

test("coalesces repeated confirmation while a delete request is in flight", async () => {
  const requestRef = { current: false };
  let releaseRequest;
  let requestCount = 0;
  const pendingRequest = new Promise((resolve) => {
    releaseRequest = resolve;
  });
  const first = runDeleteOnce(requestRef, async () => {
    requestCount += 1;
    await pendingRequest;
  });

  const duplicate = await runDeleteOnce(requestRef, async () => {
    requestCount += 1;
  });

  assert.equal(duplicate, false);
  assert.equal(requestCount, 1);
  releaseRequest();
  assert.equal(await first, true);
  assert.equal(requestRef.current, false);
});

test("returns safe messages for unauthenticated, missing, and failed deletions", async () => {
  const messages = new Map([
    [401, "Your session has ended. Sign in again before deleting this document."],
    [403, "You do not have permission to delete this document."],
    [404, "This document is no longer available. Refresh your library."],
    [409, "This document is being changed. Wait briefly, refresh your library, and retry."],
    [500, "The server returned HTTP 500. Deletion was not confirmed; refresh your library before retrying."],
  ]);
  for (const [status, message] of messages) {
    await assert.rejects(
      deleteDocument(19, "synthetic-token", async () => new Response(null, { status })),
      { message }
    );
  }
});

test("keeps confirmed deletion successful when a subsequent library refresh fails", async () => {
  let requestCount = 0;
  const fetchImpl = async () => {
    requestCount += 1;
    if (requestCount === 1) return new Response(null, { status: 204 });
    return new Response(null, { status: 503 });
  };

  const deletion = await deleteDocument(20, "synthetic-token", fetchImpl);
  const visibleDocuments = removeDeletedDocument(
    [{ id: 19 }, { id: 20 }],
    20
  );

  assert.deepEqual(visibleDocuments, [{ id: 19 }]);
  assert.deepEqual(deletion, { filesystemCleanupPending: false });
  await assert.rejects(
    refreshDocuments("synthetic-token", fetchImpl),
    { message: "The document library could not be refreshed (HTTP 503)." }
  );
  assert.deepEqual(deletion, { filesystemCleanupPending: false });
});

test("does not claim deletion succeeded when the server cannot be reached", async () => {
  await assert.rejects(
    deleteDocument(19, "synthetic-token", async () => {
      throw new TypeError("synthetic network failure");
    }),
    {
      message: "Unable to reach the server. Deletion was not confirmed; refresh your library before retrying.",
    }
  );
});
