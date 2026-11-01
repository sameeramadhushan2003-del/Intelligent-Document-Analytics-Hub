import { useEffect, useId, useRef, useState } from "react";
import Icon from "./Icon.jsx";
import "./DocumentSemanticSearch.css";

export default function DocumentSemanticSearch({ documentId, accessToken }) {
  const id = useId();
  const requestRef = useRef(null);
  const [query, setQuery] = useState("");
  const [limit, setLimit] = useState(5);
  const [results, setResults] = useState([]);
  const [hasSearched, setHasSearched] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => () => requestRef.current?.abort(), []);

  async function handleSearch(event) {
    event.preventDefault();
    if (requestRef.current) return;

    const cleanQuery = query.trim();
    if (!cleanQuery) {
      setError("Please enter a search question.");
      return;
    }

    if (!accessToken) {
      setError("Please sign in again before searching.");
      return;
    }

    const controller = new AbortController();
    requestRef.current = controller;
    setLoading(true);
    setError("");
    setResults([]);
    setHasSearched(false);

    try {
      const response = await fetch(`/api/documents/${documentId}/search`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ query: cleanQuery, limit: Number(limit) }),
        signal: controller.signal,
      });

      if (!response.ok) {
        if (response.status === 400) {
          throw new Error("Please enter a valid search question.");
        }
        if (response.status === 401) {
          throw new Error("Your session has expired. Please sign in again.");
        }
        if (response.status === 403 || response.status === 404) {
          throw new Error("Document not found or access denied.");
        }
        if (response.status === 409) {
          throw new Error("No searchable embeddings found. Generate embeddings first.");
        }
        throw new Error(`Semantic search failed (HTTP ${response.status}).`);
      }

      const data = await response.json();
      if (!Array.isArray(data.results)) {
        throw new Error("Invalid search response from the server.");
      }

      setResults(data.results);
      setHasSearched(true);
    } catch (err) {
      if (!controller.signal.aborted) {
        setError(err instanceof TypeError
          ? "Unable to connect to search. Check your connection and try again."
          : err.message || "Unable to search the document.");
      }
    } finally {
      if (!controller.signal.aborted) {
        requestRef.current = null;
        setLoading(false);
      }
    }
  }

  return (
    <section className="semantic-search" aria-label="Document semantic search">
      <div className="semantic-search-heading">
        <div>
          <span className="eyebrow"><Icon name="search" size={14} /> MEANINGFUL SEARCH</span>
          <h3>Find the passage that matters.</h3>
          <p>Search this document by meaning, not just matching words.</p>
        </div>
        <span className="semantic-document-label">Document #{documentId}</span>
      </div>

      <form className="semantic-search-form" onSubmit={handleSearch} aria-busy={loading}>
        <label htmlFor={`${id}-query`}>Your question</label>
        <textarea
          id={`${id}-query`}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="What information does this document contain?"
          rows={3}
          maxLength={10000}
          disabled={loading}
        />
        <div className="semantic-search-controls">
          <label htmlFor={`${id}-limit`}>Results</label>
          <select
            id={`${id}-limit`}
            value={limit}
            onChange={(event) => setLimit(Number(event.target.value))}
            disabled={loading}
          >
            <option value={3}>Top 3</option>
            <option value={5}>Top 5</option>
            <option value={10}>Top 10</option>
          </select>
          <button className="semantic-search-submit" type="submit" disabled={loading || !query.trim()}>
            {loading ? <><span className="loader" /> Searching…</> : <>Search document <Icon name="arrow" size={15} /></>}
          </button>
        </div>
      </form>

      {error && <p className="semantic-search-error" role="alert">{error}</p>}
      {loading && <p className="semantic-search-status" role="status">Finding relevant passages in this document…</p>}
      {hasSearched && (
        <div className="semantic-search-results" aria-live="polite">
          <h4>Relevant passages <span className="count-badge">{results.length}</span></h4>
          {results.length === 0 ? (
            <p className="semantic-search-empty">No matching passages were returned. Try asking in a different way.</p>
          ) : (
            results.map((item) => (
              <article className="semantic-result" key={item.chunkId}>
                <div className="semantic-result-heading">
                  <strong>Passage {item.chunkIndex + 1}</strong>
                  <span>Cosine similarity:{" "}
                    {typeof item.similarityScore === "number" && Number.isFinite(item.similarityScore)
                      ? item.similarityScore.toFixed(3)
                      : "N/A"}
                  </span>
                </div>
                <p>{item.chunkText}</p>
              </article>
            ))
          )}
        </div>
      )}
    </section>
  );
}
