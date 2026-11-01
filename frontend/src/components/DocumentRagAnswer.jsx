import { useEffect, useId, useRef, useState } from "react";
import "./DocumentRagAnswer.css";
import { fetchRagAnswer, RAG_STATUS_ERRORS } from "./ragAnswerRequest.js";
import Icon from "./Icon.jsx";

// Remount all question and result state whenever the selected document changes.
export default function DocumentRagAnswer(props) {
  const [session, setSession] = useState({ token: props.accessToken, version: 0 });
  if (session.token !== props.accessToken) {
    setSession({ token: props.accessToken, version: session.version + 1 });
  }
  return <AnswerPanel key={`${props.documentId}-${session.version}`} {...props} />;
}

function AnswerPanel({ documentId, documentName, accessToken, onClose }) {
  const id = useId();
  const [question, setQuestion] = useState("");
  const [answerData, setAnswerData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [copyStatus, setCopyStatus] = useState("");
  const requestRef = useRef(null);

  useEffect(() => {
    return () => {
      requestRef.current?.abort();
      requestRef.current = null;
    };
  }, [accessToken]);

  async function handleAsk(event) {
    event.preventDefault();
    if (requestRef.current) return;

    const cleanQuestion = question.trim();
    if (!cleanQuestion || cleanQuestion.length > 1000) {
      setError(RAG_STATUS_ERRORS[400]);
      return;
    }
    if (!accessToken) {
      setError("Please sign in before asking AI.");
      return;
    }

    const controller = new AbortController();
    requestRef.current = controller;
    setLoading(true);
    setError("");
    setAnswerData(null);
    setCopyStatus("");

    try {
      const data = await fetchRagAnswer({ documentId, question: cleanQuestion, accessToken, signal: controller.signal });
      if (!controller.signal.aborted) setAnswerData(data);
    } catch (err) {
      if (!controller.signal.aborted) {
        setError(err instanceof TypeError
          ? "Unable to connect to the answer service. Check your connection and that the backend is running, then try again."
          : err.message || "Unable to generate an answer. Please try again.");
      }
    } finally {
      if (!controller.signal.aborted) {
        requestRef.current = null;
        setLoading(false);
      }
    }
  }

  return (
    <section className="rag-panel" aria-labelledby={`${id}-title`}>
      <div className="rag-header">
        <div>
          <span className="eyebrow"><Icon name="spark" size={14} /> YOUR DOCUMENT ASSISTANT</span>
          <h3 id={`${id}-title`}>A question. A new connection.</h3>
          <p className="rag-document">{documentName || `Document #${documentId}`}</p>
        </div>
        <button type="button" className="rag-close" onClick={onClose}>Close Ask AI</button>
      </div>
      <p className="rag-hint">Ask a question and get an answer grounded in this document, with supporting source chunks.</p>

      <form onSubmit={handleAsk} aria-busy={loading}>
        <label htmlFor={`${id}-question`}>Your question</label>
        <textarea
          id={`${id}-question`}
          autoFocus
          rows={4}
          maxLength={1000}
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
          disabled={loading}
          aria-describedby={`${id}-hint`}
          placeholder="What are the key findings in this document?"
        />
        <div className="rag-form-footer">
          <small id={`${id}-hint`}>{question.length}/1000 characters</small>
          <button className="rag-submit" type="submit" disabled={loading || !question.trim() || !accessToken}>
            {loading ? <><span className="loader" /> Generating answer…</> : <>Ask AI <Icon name="spark" size={16} /></>}
          </button>
        </div>
      </form>

      <div role="status" aria-live="polite">
        {loading && <p className="rag-hint">AI is analyzing your document. This may take a moment.</p>}
        {answerData && <p className="rag-hint">Answer ready with {answerData.sources.length} supporting source(s).</p>}
      </div>
      {error && <p className="documents-error" role="alert">{error}</p>}
      {answerData && (
        <div className="rag-result">
          <div className="rag-answer-heading"><h4>{answerData.answer === "I could not find that information in the document." ? "Let's find a little more context" : "Your answer"}</h4><button type="button" className="rag-copy" onClick={async () => { try { await navigator.clipboard.writeText(answerData.answer); setCopyStatus("Answer copied."); } catch { setCopyStatus("Copy isn't available in this browser. Select the answer text to copy it."); } }}><Icon name="copy" size={14} /> Copy answer</button></div>
          {copyStatus && <p className="rag-hint" role="status">{copyStatus}</p>}
          <div className="rag-answer">{answerData.answer}</div>
          {typeof answerData.model === "string" && <p className="rag-hint">Model: {answerData.model}</p>}
          <h4>Document context supplied to AI ({answerData.sources.length})</h4>
          {answerData.sources.length > 0 && <p className="rag-hint">These original chunks were supplied as context. Their labels come from the application; they do not verify every claim in the answer.</p>}
          {answerData.sources.length === 0 && <p className="rag-hint">No sufficiently relevant source chunks were found. Try a more specific question.</p>}
          {answerData.sources.map((source, index) => (
            <details className="rag-source" key={`${source.chunkId}-${index}`}>
              <summary>
                Source {index + 1} · Chunk {source.chunkIndex} · Cosine similarity: {Number.isFinite(source.similarityScore) ? source.similarityScore.toFixed(3) : "N/A"}
              </summary>
              <p>{source.chunkText}</p>
            </details>
          ))}
          <p className="rag-hint">AI answers may contain mistakes. Verify important details against the supporting sources.</p>
        </div>
      )}
    </section>
  );
}
