
import { useEffect, useRef, useState } from "react";
import { useAuth } from "../context/AuthContext.jsx";
import "./Documents.css";

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

function Documents() {
  const { accessToken } = useAuth();

  const [documents, setDocuments] = useState([]);
  const [selectedFile, setSelectedFile] = useState(null);

  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);

  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const [refreshKey, setRefreshKey] = useState(0);

  const fileInputRef = useRef(null);

  const [processingDocumentId, setProcessingDocumentId] = useState(null);
  const [processMessage, setProcessMessage] = useState("");

  // Load the authenticated user's documents.
  useEffect(() => {
    if (!accessToken) {
      setLoading(false);
      return;
    }

    const controller = new AbortController();

    async function loadDocuments() {
      setLoading(true);
      setError("");

      try {
        const response = await fetch("/api/documents", {
          method: "GET",
          headers: {
            Authorization: `Bearer ${accessToken}`,
          },
          signal: controller.signal,
        });

        const data = await response.json().catch(() => null);

        if (!response.ok) {
          if (response.status === 401) {
            throw new Error(
              "Your session has expired. Please sign in again."
            );
          }

          throw new Error(
            data?.message || "Unable to load documents."
          );
        }

        if (!Array.isArray(data)) {
          throw new Error("Unexpected document library response.");
        }

        if (!controller.signal.aborted) {
          setDocuments(data);
        }

      } catch (err) {
        if (!controller.signal.aborted) {
          setError(err.message || "Unable to load documents.");
        }

      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
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
      setUploading(false);
    }
  }

  
  async function handleProcessDocument(documentId) {
    if (processingDocumentId !== null) return;

    setProcessingDocumentId(documentId);
    setProcessMessage("");

    try {
      if (!accessToken) {
        throw new Error("Please log in again.");
      }

      const response = await fetch(
        `http://localhost:8080/api/documents/${documentId}/process`,
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

      setProcessMessage(
        `Document #${result.documentId} processed successfully. ` +
        `${result.characterCount} characters extracted.`
      );

    } catch (error) {
      setProcessMessage(error.message || "Processing failed.");
    } finally {
      setProcessingDocumentId(null);
    }
  }


  return (
    <main className="documents-page">

      <div className="documents-header">
        <h1>Document Management</h1>
        <p>
          Upload and manage documents for intelligent analysis.
        </p>
      </div>

      {/* Upload section */}
      <section className="documents-upload-section">

        <h2>Upload New Document</h2>

        <p>
          Supported formats: PDF, DOCX, TXT. Maximum size: 10 MB.
        </p>

        <form onSubmit={handleUpload}>

          <div className="documents-file-input">
            <label htmlFor="document-file">
              Choose Document
            </label>

            <input
              id="document-file"
              ref={fileInputRef}
              type="file"
              accept=".pdf,.docx,.txt"
              onChange={(event) => {
                setSelectedFile(
                  event.target.files?.[0] || null
                );
                setError("");
                setSuccess("");
              }}
            />
          </div>

          {selectedFile && (
            <p className="selected-file">
              Selected: {selectedFile.name}
              {" — "}
              {formatFileSize(selectedFile.size)}
            </p>
          )}

          <button
            type="submit"
            className="documents-upload-button"
            disabled={uploading || !selectedFile}
          >
            {uploading ? "Uploading..." : "Upload Document"}
          </button>

        </form>

        {error && (
          <p className="documents-error" role="alert">
            {error}
          </p>
        )}

        {success && (
          <p className="documents-success" role="status">
            {success}
          </p>
        )}

      </section>

      {/* Document library section */}
      <section className="documents-library-section">

        <div className="documents-library-header">
          <h2>My Documents</h2>

          <button
            type="button"
            onClick={() => setRefreshKey((value) => value + 1)}
            disabled={loading}
          >
            Refresh
          </button>
        </div>

        {loading ? (
          <p>Loading your documents...</p>

        ) : documents.length === 0 ? (
          <p>
            No documents found. Upload your first document above.
          </p>

        ) : (
          <div className="documents-table-wrapper">

            {processMessage && (
              <p role="status" style={{ marginTop: "12px" }}>
                {processMessage}
              </p>
            )}

            <table className="documents-table">

              <thead>
                <tr>
                  <th>Document</th>
                  <th>Size</th>
                  <th>Status</th>
                  <th>Uploaded At</th>
                  <th>Action</th>
                </tr>
              </thead>

              <tbody>
                {documents.map((document) => (
                  <tr key={document.id}>

                    <td>
                      {document.originalFilename}
                    </td>

                    <td>
                      {formatFileSize(document.fileSizeBytes)}
                    </td>

                    <td>
                      <span className="document-status">
                        {document.processingStatus}
                      </span>
                    </td>

                    <td>
                      {document.uploadedAt
                        ? new Date(
                            document.uploadedAt
                          ).toLocaleString()
                        : "—"}
                    </td>

                    
                    <td>
                      <button
                        type="button"
                        onClick={() => handleProcessDocument(document.id)}
                        disabled={processingDocumentId !== null}
                        style={{
                          backgroundColor: "#2563eb",
                          color: "#ffffff",
                          border: "none",
                          borderRadius: "7px",
                          padding: "9px 14px",
                          cursor: processingDocumentId !== null ? "not-allowed" : "pointer",
                          opacity: processingDocumentId !== null ? 0.6 : 1,
                        }}
                      >
                        {processingDocumentId === document.id
                          ? "Processing..."
                          : "Process"}
                      </button>
                    </td>


                  </tr>
                ))}
              </tbody>

            </table>

          </div>
        )}

      </section>

    </main>
  );
}

export default Documents;
