import { useEffect, useState } from "react";
import { Link } from "react-router";
import { useAuth } from "../context/authContext.js";
import Icon from "../components/Icon.jsx";
import { documentOverview, formatBytes } from "../utils/documents.js";

function statusIcon(status) {
  const normalized = String(status || "").toUpperCase();
  if (["EXTRACTED", "INDEXED", "COMPLETED"].includes(normalized)) return "check";
  if (["PROCESSING", "PENDING"].includes(normalized)) return "clock";
  if (["FAILED", "ERROR"].includes(normalized)) return "close";
  return "file";
}

export default function Dashboard() {
  const { user, accessToken } = useAuth();
  const [result, setResult] = useState(null);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    async function load() {
      try {
        const response = await fetch("/api/documents", { headers: { Authorization: `Bearer ${accessToken}` }, signal: controller.signal });
        if (!response.ok) throw new Error(response.status === 401 ? "Your session has expired. Please sign in again." : "We couldn't load your documents. Please retry.");
        const data = await response.json();
        if (!Array.isArray(data)) throw new Error("Unexpected document response. Please retry.");
        if (!controller.signal.aborted) setResult({ data });
      } catch (error) { if (!controller.signal.aborted) setResult({ error: error.message }); }
    }
    load(); return () => controller.abort();
  }, [accessToken, retry]);
  const { total, extracted, pending, recent } = documentOverview(result?.data || []);
  return <main id="main-content" className="dashboard-page"><div className="page-heading dashboard-heading"><div><span className="eyebrow">YOUR PERSONAL KNOWLEDGE SPACE</span><h1>Welcome back, {user?.fullName?.split(" ")[0]}<span className="accent">.</span></h1><p>Your documents, ready when you are.</p></div><Link className="btn btn-primary" to="/documents#upload"><Icon name="upload" size={17} /> Upload document</Link></div>
    {result?.error && <div className="notice error" role="alert">{result.error}<button className="text-link" onClick={() => { setResult(null); setRetry(retry + 1); }}>Try again</button></div>}
    <div className="stat-grid">{[["file", "My documents", total, "In your library"], ["layers", "Text extracted", extracted, "Extracted or indexed documents"], ["clock", "Awaiting extraction", pending, "Uploaded, not yet extracted"]].map(([icon, label, value, caption], index) => <article className={`stat-card stat-card-${index + 1}`} key={label}><div><span>{label}</span><span className="stat-icon"><Icon name={icon} /></span></div><strong>{result?.data ? value : "--"}</strong><small>{caption}</small></article>)}</div>
    <section className="dashboard-banner dashboard-ai-card"><div className="dashboard-ai-copy"><span className="eyebrow"><Icon name="spark" size={14} /> ASK AI · GROUNDED IN YOUR DOCUMENT</span><h2>Find the answer.<br /><span className="muted">Keep the context.</span></h2><p>Ask a focused question and explore the source passages behind the answer.</p><Link to="/documents?tool=ask" className="btn btn-primary">Ask your documents <Icon name="arrow" size={17} /></Link></div><div className="banner-visual" aria-hidden="true"><div className="banner-file"><Icon name="file" size={40} /><span>Your documents</span></div><span className="banner-arrow"><Icon name="arrow" size={24} /></span><div className="banner-orb"><Icon name="spark" size={38} /></div></div></section>
    <div className="dashboard-columns"><section className="surface recent-documents"><div className="section-heading"><div><h2>Latest documents</h2><p>Recently added to your library</p></div><Link to="/documents" className="text-link">View all <Icon name="arrow" size={15} /></Link></div>{!result ? <div role="status" aria-label="Loading documents"><div className="skeleton" /><div className="skeleton" /><div className="skeleton" /></div> : recent.length ? <div className="recent-list">{recent.map((doc) => <Link to={`/documents?document=${doc.id}`} key={doc.id}><span className={`file-badge ${doc.originalFilename?.split(".").at(-1)?.toLowerCase()}`}>{doc.originalFilename?.split(".").at(-1)?.toUpperCase()}</span><span className="document-name"><strong>{doc.originalFilename}</strong><small>{formatBytes(doc.fileSizeBytes)} <span aria-hidden="true">·</span> {doc.uploadedAt ? new Date(doc.uploadedAt).toLocaleDateString() : "Upload date unavailable"}</small></span><span className={`badge dashboard-status status-${String(doc.processingStatus || "unknown").toLowerCase()}`}><Icon name={statusIcon(doc.processingStatus)} size={13} />{doc.processingStatus || "Unknown"}</span><Icon name="arrow" size={16} /></Link>)}</div> : <div className="empty-state"><Icon name="file" size={35} /><h3>{result.error ? "Your library is unavailable" : "Your first discovery starts here"}</h3><p>{result.error ? "Retry the connection to see your documents." : "Upload a document to build your personal knowledge workspace."}</p>{!result.error && <Link to="/documents#upload" className="btn btn-outline">Upload your first document</Link>}</div>}</section><section className="surface quick-start"><span className="eyebrow">GETTING STARTED</span><h2>A simple path to insight</h2>{[["01", "Add a document", "Choose a PDF, DOCX or TXT file."], ["02", "Prepare it", "Extract text, then create chunks and embeddings."], ["03", "Explore", "Search passages or ask a question with sources."]].map(([n, title, desc]) => <div className="guide-step" key={n}><span>{n}</span><div><h3>{title}</h3><p>{desc}</p></div></div>)}<Link to="/documents?tool=search" className="text-link">Open semantic search <Icon name="arrow" size={15} /></Link></section></div>
  </main>;
}
