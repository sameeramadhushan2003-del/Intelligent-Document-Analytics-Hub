export async function deleteDocument(documentId, accessToken, fetchImpl = fetch) {
  let response;
  try {
    response = await fetchImpl(`/api/documents/${documentId}`, {
      method: "DELETE",
      headers: { Authorization: ["Bearer", accessToken].join(" ") },
    });
  } catch {
    throw new Error(
      "Unable to reach the server. Deletion was not confirmed; refresh your library before retrying."
    );
  }

  if (response.ok) {
    if (response.status === 202) {
      return { filesystemCleanupPending: true };
    }
    return { filesystemCleanupPending: false };
  }

  if (response.status === 401) {
    throw new Error("Your session has ended. Sign in again before deleting this document.");
  }
  if (response.status === 403) {
    throw new Error("You do not have permission to delete this document.");
  }
  if (response.status === 404) {
    throw new Error("This document is no longer available. Refresh your library.");
  }
  if (response.status === 503) {
    throw new Error("Deletion could not be completed safely. Refresh your library and retry shortly.");
  }
  if (response.status === 409) {
    throw new Error("This document is being changed. Wait briefly, refresh your library, and retry.");
  }

  throw new Error(
    `The server returned HTTP ${response.status}. Deletion was not confirmed; refresh your library before retrying.`
  );
}

export async function runDeleteOnce(requestRef, request) {
  if (requestRef.current) return false;
  requestRef.current = true;
  try {
    await request();
    return true;
  } finally {
    requestRef.current = false;
  }
}

export function removeDeletedDocument(documents, documentId) {
  return documents.filter((document) => document.id !== documentId);
}
