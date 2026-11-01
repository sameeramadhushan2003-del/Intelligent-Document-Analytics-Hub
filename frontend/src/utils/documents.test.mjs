import test from "node:test";
import assert from "node:assert/strict";
import { documentOverview, filterDocuments, sortDocuments, validateUpload, formatBytes } from "./documents.js";
const documents = [{ id: 1, title: "Clinic", originalFilename: "Clinic.pdf", processingStatus: "EXTRACTED", uploadedAt: "2026-01-01" }, { id: 2, title: "Notes", originalFilename: "Notes.txt", processingStatus: "UPLOADED", uploadedAt: "2026-02-01" }];
test("dashboard derives counts and latest documents from real response records", () => {
  const stats = documentOverview(documents); assert.equal(stats.total, 2); assert.equal(stats.extracted, 1); assert.equal(stats.pending, 1); assert.equal(stats.recent[0].id, 2); assert.equal(documents[0].id, 1);
});
test("empty dashboard has no invented activity or counts", () => { assert.deepEqual(documentOverview([]), { total: 0, extracted: 0, pending: 0, recent: [] }); });
test("library filters by filename and actual status", () => { assert.equal(filterDocuments(documents, "clinic", "EXTRACTED").length, 1); assert.equal(filterDocuments(documents, "clinic", "UPLOADED").length, 0); });
test("library searches titles and preserves selectable document sort orders", () => {
  const byTitle = [{ id: 1, title: "zeta", uploadedAt: "2026-01-01" }, { id: 2, title: "Alpha", uploadedAt: "2026-03-01" }];
  assert.equal(filterDocuments(byTitle, "alpha", "").length, 1);
  assert.deepEqual(sortDocuments(documents).map(document => document.id), [2, 1]);
  assert.deepEqual(sortDocuments(documents, "oldest").map(document => document.id), [1, 2]);
  assert.deepEqual(sortDocuments(byTitle, "title").map(document => document.id), [2, 1]);
  assert.deepEqual(documents.map(document => document.id), [1, 2]);
});
test("upload selection rejects invalid file types, empty and oversized documents", () => {
  for (const file of [null, { name: "bad.exe", size: 2 }, { name: "empty.txt", size: 0 }, { name: "big.pdf", size: 10485761 }]) assert.ok(validateUpload(file));
  assert.equal(validateUpload({ name: "OK.DOCX", size: 100 }), ""); assert.equal(formatBytes(1024), "1.0 KB");
});
