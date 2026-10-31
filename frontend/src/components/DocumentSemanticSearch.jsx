
import { useState } from "react";

export default function DocumentSemanticSearch({
  documentId,
  accessToken,
}) {
  const [query, setQuery] = useState("");
  const [limit, setLimit] = useState(5);
  const [results, setResults] = useState([]);
  const [hasSearched, setHasSearched] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function handleSearch(event) {
    event.preventDefault();

    if (loading) return;

    const cleanQuery = query.trim();

    if (!cleanQuery) {
      setError("Please enter a search question.");
      return;
    }

    if (!accessToken) {
      setError("Please log in again.");
      return;
    }

    setLoading(true);
    setError("");
    setResults([]);
    setHasSearched(false);

    try {
      const response = await fetch(
        `http://localhost:8080/api/documents/${documentId}/search`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            query: cleanQuery,
            limit: Number(limit),
          }),
        }
      );

      if (!response.ok) {
        if (response.status === 400) {
          throw new Error("Please enter a valid search question.");
        }

        if (response.status === 401) {
          throw new Error(
            "Your session has expired. Please log in again."
          );
        }

        if (response.status === 403 ||
            response.status === 404) {
          throw new Error("Document not found or access denied.");
        }

        if (response.status === 409) {
          throw new Error(
            "No searchable embeddings found. Generate embeddings first."
          );
        }

        throw new Error(
          `Semantic search failed (HTTP ${response.status}).`
        );
      }

      const data = await response.json();

      if (!Array.isArray(data.results)) {
        throw new Error("Invalid search response from the server.");
      }

      setResults(data.results);
      setHasSearched(true);
    } catch (err) {
      setError(
        err.message || "Unable to search the document."
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <section
      aria-label={`Semantic search for document ${documentId}`}
      style={{
        marginTop: "20px",
        padding: "24px",
        backgroundColor: "#ffffff",
        border: "1px solid #e2e8f0",
        borderRadius: "12px",
      }}
    >
      <h3 style={{ marginTop: 0, color: "#0f172a" }}>
        Semantic Search — Document #{documentId}
      </h3>

      <p style={{ color: "#64748b" }}>
        Ask a question and find relevant information
        from your uploaded document.
      </p>

      <form onSubmit={handleSearch}>
        <label
          htmlFor="document-semantic-query"
          style={{
            display: "block",
            marginBottom: "8px",
            fontWeight: 600,
          }}
        >
          Your Question
        </label>

        <textarea
          id="document-semantic-query"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="What information does this document contain?"
          rows={3}
          maxLength={10000}
          disabled={loading}
          style={{
            width: "100%",
            padding: "12px",
            border: "1px solid #cbd5e1",
            borderRadius: "8px",
            boxSizing: "border-box",
            fontFamily: "inherit",
            resize: "vertical",
          }}
        />

        <div
          style={{
            display: "flex",
            gap: "12px",
            alignItems: "center",
            flexWrap: "wrap",
            marginTop: "14px",
          }}
        >
          <label htmlFor="semantic-result-limit">
            Results:
          </label>

          <select
            id="semantic-result-limit"
            value={limit}
            onChange={(event) =>
              setLimit(Number(event.target.value))
            }
            disabled={loading}
            style={{
              padding: "9px",
              borderRadius: "7px",
            }}
          >
            <option value={3}>Top 3</option>
            <option value={5}>Top 5</option>
            <option value={10}>Top 10</option>
          </select>

          <button
            type="submit"
            disabled={loading || !query.trim()}
            style={{
              padding: "11px 20px",
              border: "none",
              borderRadius: "8px",
              backgroundColor: "#2563eb",
              color: "#ffffff",
              cursor: loading ? "wait" : "pointer",
            }}
          >
            {loading ? "Searching..." : "Search Document"}
          </button>
        </div>
      </form>

      {error && (
        <p
          role="alert"
          style={{
            marginTop: "16px",
            color: "#b91c1c",
          }}
        >
          {error}
        </p>
      )}

      {hasSearched && (
        <div style={{ marginTop: "24px" }}>
          <h4 style={{ color: "#0f172a" }}>
            Search Results ({results.length})
          </h4>

          {results.length === 0 && (
            <p>No matching document chunks were returned.</p>
          )}

          {results.map((item) => (
            <article
              key={item.chunkId}
              style={{
                border: "1px solid #e2e8f0",
                borderRadius: "10px",
                padding: "16px",
                marginTop: "12px",
                backgroundColor: "#f8fafc",
              }}
            >
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  gap: "12px",
                  flexWrap: "wrap",
                  marginBottom: "10px",
                }}
              >
                <strong>
                  Chunk {item.chunkIndex}
                </strong>

                <span style={{ color: "#0f766e" }}>
                  Cosine similarity:{" "}
                  {typeof item.similarityScore === "number"
                    && Number.isFinite(item.similarityScore)
                    ? item.similarityScore.toFixed(3)
                    : "N/A"}
                </span>
              </div>

              <p
                style={{
                  whiteSpace: "pre-wrap",
                  overflowWrap: "anywhere",
                  color: "#334155",
                  lineHeight: 1.7,
                  margin: 0,
                }}
              >
                {item.chunkText}
              </p>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
