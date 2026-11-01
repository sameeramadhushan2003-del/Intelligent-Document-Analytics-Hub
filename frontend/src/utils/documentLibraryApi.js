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
