export const RAG_STATUS_ERRORS = {
  400: "Please enter a valid question of at most 1000 characters.",
  401: "Your session has expired. Please sign in again.",
  403: "You do not have permission to ask questions about this document.",
  404: "This document could not be found. Refresh your document library.",
  409: "This document is not ready. Process it, generate chunks, then generate embeddings before asking AI.",
  502: "The AI service could not produce a valid answer. Please try again.",
  503: "The AI service is unavailable. Check that Ollama and its models are running, then retry.",
  504: "AI answer generation timed out. Please try a shorter question or retry shortly.",
};

// Keep HTTP handling testable without a browser or additional dependencies.
export async function fetchRagAnswer({ documentId, question, accessToken, signal }, fetchImpl = fetch) {
  const response = await fetchImpl(`/api/documents/${documentId}/ask`, {
    method: "POST",
    headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
    body: JSON.stringify({ question }),
    signal,
  });
  if (!response.ok) {
    throw new Error(RAG_STATUS_ERRORS[response.status] ||
      (response.status >= 500
        ? "The answer service is unavailable. Please try again shortly."
        : `Unable to generate an answer (HTTP ${response.status}).`));
  }
  const data = await response.json().catch(() => null);
  if (!data || typeof data.answer !== "string" || !Array.isArray(data.sources) ||
      String(data.documentId) !== String(documentId) ||
      !data.sources.every((source) => source && typeof source.chunkText === "string" &&
        Number.isInteger(source.chunkIndex) && source.chunkIndex >= 0 &&
        Number.isFinite(source.similarityScore) && source.similarityScore >= -1 && source.similarityScore <= 1)) {
    throw new Error("The server returned an invalid AI response. Please try again.");
  }
  return data;
}
