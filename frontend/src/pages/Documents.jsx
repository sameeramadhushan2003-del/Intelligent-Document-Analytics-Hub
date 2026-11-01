
import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router";
import Icon from "../components/Icon.jsx";
import { filterDocuments, validateUpload } from "../utils/documents.js";
import {
  deleteDocument,
  removeDeletedDocument,
  runDeleteOnce,
} from "../utils/documentDeletion.js";
import { refreshDocuments } from "../utils/documentLibraryApi.js";
import { embeddingErrorMessage } from "../utils/documentEmbeddings.js";
import { useAuth } from "../context/authContext.js";
import "./Documents.css";
import "./DocumentsPremium.css";
import "./DocumentDeletion.css";
import DocumentSemanticSearch from "../components/DocumentSemanticSearch";
import DocumentRagAnswer from "../components/DocumentRagAnswer.jsx";

const MAX_FILE_SIZE = 10 * 1024 * 1024;

function formatFileSize(bytes) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }

  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }

  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function DocumentStatus({ status }) {
  const normalized = String(status || "UNKNOWN").toUpperCase();
  const icon = normalized === "FAILED" || normalized === "ERROR"
    ? "close"
    : ["EXTRACTED", "INDEXED", "COMPLETED"].includes(normalized)
      ? "check"
      : normalized === "PROCESSING"
        ? "clock"
        : "file";

  return (
    <span className={`badge status-${normalized.toLowerCase()}`}>
      <Icon className="status-icon" name={icon} size={14} />
      {normalized.charAt(0) + normalized.slice(1).toLowerCase()}
    </span>
  );
}

function Documents() {
  const { accessToken } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [dragging, setDragging] = useState(false);
  const actionRef = useRef(false);
  const uploadRef = useRef(false);

  const [documents, setDocuments] = useState([]);
  const [selectedFile, setSelectedFile] = useState(null);

  const [completedLoad, setCompletedLoad] = useState(null);
  const [uploading, setUploading] = useState(false);

  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const [refreshKey, setRefreshKey] = useState(0);
  const loading = Boolean(accessToken) &&
    (completedLoad?.accessToken !== accessToken || completedLoad?.refreshKey !== refreshKey);

  const fileInputRef = useRef(null);

  const [processingDocumentId, setProcessingDocumentId] = useState(null);
  const [processMessage, setProcessMessage] = useState("");
  const [generatingDocumentId, setGeneratingDocumentId] = useState(null);
  const [generatingEmbeddingDocumentId, setGeneratingEmbeddingDocumentId] =
    useState(null);
  const [searchDocumentId, setSearchDocumentId] = useState(null);
  const [askDocumentId, setAskDocumentId] = useState(null);
  const askActionRef = useRef(null);
  const deleteDialogRef = useRef(null);
  const deleteCancelRef = useRef(null);
  const deleteTriggerRef = useRef(null);
  const libraryHeadingRef = useRef(null);
  const focusAfterCloseRef = useRef(null);
  const dialogWasOpenRef = useRef(false);
  const deleteRequestRef = useRef(false);
  const [confirmingDelete, setConfirmingDelete] = useState(null);
  const [deletingDocumentId, setDeletingDocumentId] = useState(null);
  const [deleteError, setDeleteError] = useState("");
  const [toast, setToast] = useState(null);

  useEffect(() => {
    if (!toast) return undefined;
    const timer = setTimeout(() => setToast(null), 5000);
    return () => clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    const dialog = deleteDialogRef.current;
    if (confirmingDelete) {
      if (!dialog.open) dialog.showModal();
      dialogWasOpenRef.current = true;
      deleteCancelRef.current?.focus();
    } else if (dialogWasOpenRef.current) {
      if (dialog.open) dialog.close();
      dialogWasOpenRef.current = false;
      const returnFocus = focusAfterCloseRef.current || deleteTriggerRef.current;
      focusAfterCloseRef.current = null;
      returnFocus?.focus();
    }
  }, [confirmingDelete]);

  function requestDocumentDeletion(document, event) {
    deleteTriggerRef.current = event.currentTarget;
    focusAfterCloseRef.current = null;
    setDeleteError("");
    setConfirmingDelete(document);
  }

  async function confirmDocumentDeletion() {
    if (!confirmingDelete) return;
    const documentToDelete = confirmingDelete;

    await runDeleteOnce(deleteRequestRef, async () => {
      setDeletingDocumentId(documentToDelete.id);
      setDeleteError("");

      try {
        const deletion = await deleteDocument(documentToDelete.id, accessToken);
        setDocuments((current) => removeDeletedDocument(current, documentToDelete.id));
        setRefreshKey((current) => current + 1);
        setSearchDocumentId((current) => current === documentToDelete.id ? null : current);
        setAskDocumentId((current) => current === documentToDelete.id ? null : current);
        if (askActionRef.current?.isConnected) askActionRef.current = null;
        setSearchParams((current) => {
          const next = new URLSearchParams(current);
          if (next.get("document") === String(documentToDelete.id)) next.delete("document");
          return next;
        }, { replace: true });
        setToast({
          type: "success",
          message: deletion.filesystemCleanupPending
            ? `${documentToDelete.originalFilename} was removed. File cleanup is pending.`
            : `${documentToDelete.originalFilename} was deleted.`,
        });
        focusAfterCloseRef.current = libraryHeadingRef.current;
        setConfirmingDelete(null);
      } catch (error) {
        setDeleteError(error.message || "We couldn't delete this document. Please try again.");
        setToast({ type: "error", message: error.message || "We couldn't delete this document. Please try again." });
      } finally {
        setDeletingDocumentId(null);
      }
    });
  }

  function closeAskAI() {
    setAskDocumentId(null);
    askActionRef.current?.focus();
  }

  // Load the authenticated user's documents.
  useEffect(() => {
    if (!accessToken) {
      return;
    }

    const controller = new AbortController();

    async function loadDocuments() {
      try {
        const data = await refreshDocuments(
          accessToken,
          fetch,
          controller.signal
        );

        if (!controller.signal.aborted) {
          setDocuments(data);
          setError("");
        }

      } catch (err) {
        if (!controller.signal.aborted) {
          setError(err.message || "Unable to load documents.");
        }

      } finally {
        if (!controller.signal.aborted) {
          setCompletedLoad({ accessToken, refreshKey });
        }
      }
    }

    loadDocuments();

    return () => controller.abort();

  }, [accessToken, refreshKey]);

  // Upload a document to Spring Boot.
  async function handleUpload(event) {
    event.preventDefault();

    setError("");
    setSuccess("");

    if (!selectedFile) {
      setError("Please select a document first.");
      return;
    }

    if (selectedFile.size > MAX_FILE_SIZE) {
      setError("Maximum allowed file size is 10 MB.");
      return;
    }

    if (!/\.(pdf|docx|txt)$/i.test(selectedFile.name)) {
      setError("Only PDF, DOCX and TXT files are supported.");
      return;
    }

    if (uploadRef.current) return;
    uploadRef.current = true;
    setUploading(true);

    try {
      const formData = new FormData();
      formData.append("file", selectedFile);

      const response = await fetch("/api/documents/upload", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: formData,
      });

      const data = await response.json().catch(() => null);

      if (!response.ok) {
        if (response.status === 401) {
          throw new Error(
            "Your session has expired. Please sign in again."
          );
        }

        if (response.status === 413) {
          throw new Error("Maximum allowed file size is 10 MB.");
        }

        throw new Error(
          data?.message || "Document upload failed."
        );
      }

      setSuccess(
        `Document uploaded successfully! ID: ${data.documentId}`
      );

      setSelectedFile(null);

      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }

      // Refresh the document list after upload.
      setRefreshKey((previous) => previous + 1);

    } catch (err) {
      setError(err.message || "Unable to upload document.");

    } finally {
      uploadRef.current = false;
      setUploading(false);
    }
  }

  
  async function handleProcessDocument(documentId) {
    if (processingDocumentId !== null) return;

    if (actionRef.current) return;
      actionRef.current = true;
      setAskDocumentId(null);
      setSearchDocumentId(null);
      setProcessingDocumentId(documentId);
    setProcessMessage("");

    try {
      if (!accessToken) {
        throw new Error("Please log in again.");
      }

      const response = await fetch(
        `/api/documents/${documentId}/process`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
        }
      );

      if (!response.ok) {
        if (response.status === 401) {
          throw new Error("Session expired. Please log in again.");
        }

        if (response.status === 404) {
          throw new Error("Document not found or access denied.");
        }

        if (response.status === 422) {
          throw new Error("No readable text could be extracted.");
        }

        throw new Error(`Processing failed (HTTP ${response.status}).`);
      }

      const result = await response.json();
      setRefreshKey(value => value + 1);

      setProcessMessage(
        `Document #${result.documentId} processed successfully. ` +
        `${result.characterCount} characters extracted.`
      );

    } catch (error) {
      setProcessMessage(error.message || "Processing failed.");
    } finally {
      actionRef.current = false;
      setProcessingDocumentId(null);
    }
  }

  
    async function handleGenerateChunks(documentId) {
      if (
        generatingDocumentId !== null ||
        processingDocumentId !== null
      ) {
        return;
      }

      if (actionRef.current) return;
      actionRef.current = true;
      setAskDocumentId(null);
      setSearchDocumentId(null);
      setGeneratingDocumentId(documentId);
      setProcessMessage("");

      try {
        if (!accessToken) {
          throw new Error("Please log in again.");
        }

        const response = await fetch(
          `/api/documents/${documentId}/chunks/generate`,
          {
            method: "POST",
            headers: {
              Authorization: `Bearer ${accessToken}`,
            },
          }
        );

        if (!response.ok) {
          if (response.status === 401) {
            throw new Error(
              "Your session has expired. Please log in again."
            );
          }

          if (response.status === 404) {
            throw new Error(
              "Document not found or access denied."
            );
          }

          if (response.status === 409) {
            throw new Error(
              "Please process the document before generating chunks."
            );
          }

          throw new Error(
            `Chunk generation failed (HTTP ${response.status}).`
          );
        }

        const result = await response.json();
      setRefreshKey(value => value + 1);

        setProcessMessage(
          `Document #${result.documentId}: ` +
          `${result.chunkCount} chunk(s) generated and saved successfully.`
        );

      } catch (error) {
        setProcessMessage(
          error.message || "Unable to generate document chunks."
        );
      } finally {
        actionRef.current = false;
      setGeneratingDocumentId(null);
      }
    }

    
    async function handleGenerateEmbeddings(documentId) {
      if (
        generatingEmbeddingDocumentId !== null ||
        generatingDocumentId !== null ||
        processingDocumentId !== null
      ) {
        return;
      }

      if (actionRef.current) return;
      actionRef.current = true;
      setAskDocumentId(null);
      setSearchDocumentId(null);
      setGeneratingEmbeddingDocumentId(documentId);
      setProcessMessage("");

      try {
        if (!accessToken) {
          throw new Error("Please log in again.");
        }

        const response = await fetch(
          `/api/documents/${documentId}/embeddings/generate`,
          {
            method: "POST",
            headers: {
              Authorization: `Bearer ${accessToken}`,
            },
          }
        );

        if (!response.ok) {
          if (response.status === 401) {
            throw new Error(
              "Your session has expired. Please log in again."
            );
          }

          if (response.status === 403 ||
              response.status === 404) {
            throw new Error(
              "Document not found or access denied."
            );
          }

          if (response.status === 409) {
            throw new Error(
              "Please generate document chunks first."
            );
          }

          throw new Error(embeddingErrorMessage(response.status));
        }

        const result = await response.json();
      setRefreshKey(value => value + 1);

        setProcessMessage(
          `Document #${result.documentId}: ` +
          `${result.embeddingCount} embedding(s) generated and saved successfully.`
        );

      } catch (error) {
        setProcessMessage(
          error.message || "Unable to generate embeddings."
        );
      } finally {
        actionRef.current = false;
      setGeneratingEmbeddingDocumentId(null);
      }
    }




  const busy = uploading || processingDocumentId !== null || generatingDocumentId !== null || generatingEmbeddingDocumentId !== null;
  const visible = filterDocuments(documents, query, statusFilter);
  function selectFile(file) {
    const message = validateUpload(file);
    setError(message); setSuccess("");
    setSelectedFile(message ? null : file);
  }
  return <main id="main-content" className="documents-page">
    <div className="page-heading"><div><span className="eyebrow">A HOME FOR YOUR KNOWLEDGE</span><h1>My documents<span className="accent">.</span></h1><p>Your original documents. New ways to understand them.</p></div><a className="btn btn-primary" href="#upload"><Icon name="upload" size={17} /> Add document</a></div>
    <section id="upload" className="documents-upload-section surface"><div className="section-heading"><div><h2>Bring something worth exploring.</h2><p>PDF, DOCX or TXT | Up to 10 MB | Private to your account</p></div><Icon name="shield" size={22} /></div>
      <form onSubmit={handleUpload}>
        <div className={`upload-dropzone ${dragging ? "dragging" : ""}`} onDragOver={event => { event.preventDefault(); setDragging(true); }} onDragLeave={() => setDragging(false)} onDrop={event => { event.preventDefault(); setDragging(false); if (!busy) { if (event.dataTransfer.files.length !== 1) setError("Please drop one document at a time."); else selectFile(event.dataTransfer.files[0]); } }}>
          <span className="upload-symbol"><Icon name="upload" size={28} /></span><div><strong>{selectedFile ? selectedFile.name : "Drop your next discovery here"}</strong><p>{selectedFile ? `${formatFileSize(selectedFile.size)} | Ready to upload` : "or choose a document from your device"}</p></div><input className="sr-only" id="document-file" ref={fileInputRef} type="file" accept=".pdf,.docx,.txt" disabled={busy} aria-label="Choose PDF DOCX or TXT document" onChange={event => selectFile(event.target.files?.[0] || null)} /><button type="button" className="btn btn-outline" disabled={busy} onClick={() => fileInputRef.current?.click()}>Browse files</button>
        </div><div className="upload-footer"><span className="muted"><Icon name="shield" size={14} /> Protected by document ownership</span><button type="submit" className="btn btn-primary" disabled={busy || !selectedFile}>{uploading ? <><span className="loader" /> Uploading...</> : <>Upload document <Icon name="arrow" size={16} /></>}</button></div>
      </form>
      {error && <p className="notice error" role="alert">{error}</p>}{success && <p className="notice success" role="status"><Icon name="check" size={16} />{success}</p>}
    </section>
    <ol className="workspace-pipeline" aria-label="Typical document workflow">
      <li className="pipeline-intro">
        <strong>From file to insight</strong>
        <span>Typical workflow · Status is shown on each document</span>
      </li>
      {[
        ["Upload", "upload"],
        ["Extract", "file"],
        ["Create chunks", "layers"],
        ["Generate embeddings", "spark"],
        ["Search", "search"],
        ["Ask AI", "spark"],
      ].map(([step, icon], index) => (
        <li key={step}>
          <span className="pipeline-step-icon"><Icon name={icon} size={18} /></span>
          <span className="pipeline-step-copy">
            <b>STEP 0{index + 1}</b>
            <span>{step}</span>
          </span>
        </li>
      ))}
    </ol>
    {toast && <div className={`document-toast ${toast.type}`} role={toast.type === "error" ? "alert" : "status"}><Icon name={toast.type === "success" ? "check" : "close"} size={16} /><span>{toast.message}</span><button type="button" onClick={() => setToast(null)} aria-label="Dismiss notification"><Icon name="close" size={15} /></button></div>}
    <section className="documents-library-section surface"><div className="section-heading"><div><h2 ref={libraryHeadingRef} tabIndex={-1}>Your library <span className="count-badge">{documents.length}</span></h2><p>Prepare a document, then follow your questions.</p></div><button type="button" className="btn btn-outline btn-small" disabled={loading || busy} onClick={() => setRefreshKey(value => value + 1)}>Refresh library</button></div>
      <div className="library-filters"><label className="search-field"><Icon name="search" size={18} /><input value={query} onChange={event => setQuery(event.target.value)} placeholder="Find a document by name..." aria-label="Filter documents by filename" /></label><label className="sr-only" htmlFor="status-filter">Filter by processing status</label><select id="status-filter" value={statusFilter} onChange={event => setStatusFilter(event.target.value)}><option value="">All statuses</option>{["UPLOADED", "EXTRACTED", "INDEXED", "PROCESSING", "FAILED"].map(value => <option key={value} value={value}>{value}</option>)}</select></div>
      {searchParams.get("tool") && <p className="notice">Choose {searchParams.get("tool") === "ask" ? "Ask AI" : "Search"} beside a prepared document below. Embeddings are required.</p>}
      {processMessage && <p className="notice" role="status">{processMessage}</p>}
      {loading ? <div role="status" aria-label="Loading your documents"><div className="skeleton" /><div className="skeleton" /><div className="skeleton" /></div> : visible.length === 0 ? <div className="empty-state"><Icon name="file" size={36} /><h3>{documents.length ? "No matching documents" : "A blank page. A new possibility."}</h3><p>{documents.length ? "Try a different name or status filter." : "Upload your first document above. We'll help you find the context within."}</p>{documents.length > 0 && <button className="btn btn-outline" onClick={() => { setQuery(""); setStatusFilter(""); }}>Clear filters</button>}</div> : <div className="documents-table-wrapper"><table className="documents-table"><thead><tr><th>Document</th><th>Status</th><th>Uploaded</th><th>Prepare & explore</th></tr></thead><tbody>{visible.map(document => <tr key={document.id} className={searchParams.get("document") === String(document.id) ? "selected-row" : ""}><td><span className="mobile-cell-label">Document</span><div className="document-cell"><span className={`file-badge ${document.originalFilename?.split(".").at(-1)?.toLowerCase()}`}>{document.originalFilename?.split(".").at(-1)?.toUpperCase()}</span><span className="document-name"><strong title={document.originalFilename}>{document.originalFilename}</strong><small>{formatFileSize(document.fileSizeBytes)} | #{document.id}</small></span></div></td><td><span className="mobile-cell-label">Status</span><DocumentStatus status={document.processingStatus} /></td><td className="date-cell"><span className="mobile-cell-label">Uploaded</span>{document.uploadedAt ? new Date(document.uploadedAt).toLocaleDateString() : "Unavailable"}</td><td><span className="mobile-cell-label">Actions</span><div className="document-actions"><button disabled={busy} onClick={() => handleProcessDocument(document.id)}><Icon name="file" size={16} />{processingDocumentId === document.id ? "Extracting..." : "Extract"}</button><button disabled={busy || !["EXTRACTED", "INDEXED"].includes(document.processingStatus)} onClick={() => handleGenerateChunks(document.id)}><Icon name="layers" size={16} />{generatingDocumentId === document.id ? "Generating..." : "Chunks"}</button><button disabled={busy || !["EXTRACTED", "INDEXED"].includes(document.processingStatus)} onClick={() => handleGenerateEmbeddings(document.id)}><Icon name="spark" size={16} />{generatingEmbeddingDocumentId === document.id ? "Generating..." : "Embeddings"}</button><button disabled={busy || !["EXTRACTED", "INDEXED"].includes(document.processingStatus)} aria-expanded={searchDocumentId === document.id} onClick={() => setSearchDocumentId(current => current === document.id ? null : document.id)}><Icon name="search" size={16} />{searchDocumentId === document.id ? "Close search" : "Search"}</button><button className="ask-action" disabled={busy} aria-expanded={askDocumentId === document.id} aria-controls={askDocumentId === document.id ? "document-ask-panel" : undefined} onClick={event => { askActionRef.current = event.currentTarget; setAskDocumentId(current => current === document.id ? null : document.id); }}><Icon name="spark" size={16} />{askDocumentId === document.id ? "Close AI" : "Ask AI"}</button><button className="delete-action" disabled={busy || deletingDocumentId !== null} onClick={event => requestDocumentDeletion(document, event)} aria-label={`Delete ${document.originalFilename}`}><Icon name="trash" size={16} />Delete</button></div></td></tr>)}</tbody></table></div>}
      <p className="library-footnote"><Icon name="layers" size={14} /> Preparation order: Extract &rarr; Chunks &rarr; Embeddings. Status reflects extraction; it does not confirm embeddings exist.</p>
    </section>
    {searchDocumentId !== null && documents.some(document => document.id === searchDocumentId) && <DocumentSemanticSearch key={searchDocumentId} documentId={searchDocumentId} accessToken={accessToken} />}
    {askDocumentId !== null && documents.some(document => document.id === askDocumentId) && <div id="document-ask-panel"><DocumentRagAnswer documentId={askDocumentId} documentName={documents.find(document => document.id === askDocumentId)?.originalFilename} accessToken={accessToken} onClose={closeAskAI} /></div>}
    <dialog ref={deleteDialogRef} className="delete-dialog" aria-labelledby="delete-dialog-title" aria-describedby="delete-dialog-description" onKeyDown={(event) => { if (event.key === "Escape" && deletingDocumentId === null) { event.preventDefault(); setConfirmingDelete(null); } }} onCancel={(event) => { event.preventDefault(); if (deletingDocumentId === null) setConfirmingDelete(null); }}>
      {confirmingDelete && <><span className="delete-dialog-icon"><Icon name="trash" size={22} /></span><span className="eyebrow">REMOVE FROM YOUR WORKSPACE</span><h2 id="delete-dialog-title">Delete this document?</h2><p className="delete-file-name">{confirmingDelete.originalFilename}</p><p id="delete-dialog-description">This permanently removes the original file, extracted text, chunks, and saved embeddings from your workspace. This action cannot be undone.</p>{deleteError && <p className="delete-dialog-error" role="alert">{deleteError}</p>}<div className="delete-dialog-actions"><button ref={deleteCancelRef} type="button" className="btn btn-outline" disabled={deletingDocumentId !== null} onClick={() => setConfirmingDelete(null)}>Cancel</button><button type="button" className="btn btn-danger" disabled={deletingDocumentId !== null} onClick={confirmDocumentDeletion}>{deletingDocumentId !== null ? <><span className="loader" /> Deleting…</> : <><Icon name="trash" size={16} /> Confirm delete</>}</button></div></>}
    </dialog>
  </main>;
}
export default Documents;
