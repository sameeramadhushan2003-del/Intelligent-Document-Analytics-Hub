const messages = new Map([
  [400, "The embedding request is invalid."],
  [401, "Your session has expired. Please sign in again."],
  [403, "You do not have permission to change this document."],
  [404, "Document not found or access denied."],
  [409, "Please generate document chunks before generating embeddings."],
  [502, "The embedding service returned an invalid response. Please retry."],
  [503, "The embedding service or database is unavailable. Please retry shortly."],
  [504, "Embedding generation timed out. Existing embeddings are preserved; please retry."],
]);

export function embeddingErrorMessage(status) {
  return messages.get(status) ||
    "Embedding generation could not be completed. Please retry.";
}
