export async function refreshDocuments(
  accessToken,
  fetchImpl = fetch,
  signal
) {
  let response;
  try {
    response = await fetchImpl("/api/documents", {
      method: "GET",
      headers: { Authorization: ["Bearer", accessToken].join(" ") },
      signal,
    });
  } catch (error) {
    if (error?.name === "AbortError") throw error;
    throw new Error(
      "Unable to reach the server. Your document library could not be refreshed.",
      { cause: error }
    );
  }

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error("Your session has expired. Please sign in again.");
    }
    throw new Error(
      `The document library could not be refreshed (HTTP ${response.status}).`
    );
  }

  const documents = await response.json().catch(() => null);
  if (!Array.isArray(documents)) {
    throw new Error("The document library returned an unexpected response.");
  }
  return documents;
}

export async function updateDocumentTitle(
  documentId,
  title,
  accessToken,
  fetchImpl = fetch
) {
  let response;
  try {
    response = await fetchImpl(`/api/documents/${documentId}`, {
      method: "PATCH",
      headers: {
        Authorization: ["Bearer", accessToken].join(" "),
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ title }),
    });
  } catch (error) {
    throw new Error(
      "Unable to reach the server. The document title was not updated.",
      { cause: error }
    );
  }

  const document = await response.json().catch(() => null);
  if (!response.ok) {
    const message = response.status === 401
      ? "Your session has expired. Please sign in again."
      : response.status === 404
        ? "This document is no longer available. Refresh your library."
        : response.status === 400
          ? "Enter a title with 1 to 255 characters."
          : document?.message || `The document title could not be updated (HTTP ${response.status}).`;
    throw new Error(message);
  }

  if (!document || document.id === undefined || typeof document.title !== "string") {
    throw new Error("The document update returned an unexpected response.");
  }
  return document;
}
