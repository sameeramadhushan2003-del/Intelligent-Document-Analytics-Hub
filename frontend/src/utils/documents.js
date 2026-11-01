export function formatBytes(bytes = 0) {
  return bytes < 1024 ? `${bytes} B` : bytes < 1048576 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1048576).toFixed(2)} MB`;
}
export function documentOverview(documents) {
  return { total: documents.length, extracted: documents.filter(d => ["EXTRACTED", "INDEXED"].includes(d.processingStatus)).length,
    pending: documents.filter(d => d.processingStatus === "UPLOADED").length,
    recent: [...documents].sort((a, b) => (Date.parse(b.uploadedAt) || 0) - (Date.parse(a.uploadedAt) || 0)).slice(0, 4) };
}
export function filterDocuments(documents, query, status) {
  const search = query.toLowerCase();
  return documents.filter(d => (!status || d.processingStatus === status) &&
    `${d.title || ""} ${d.originalFilename || ""}`.toLowerCase().includes(search));
}
export function sortDocuments(documents, sortBy = "newest") {
  return [...documents].sort((a, b) => {
    if (sortBy === "oldest") {
      return (Date.parse(a.uploadedAt) || 0) - (Date.parse(b.uploadedAt) || 0);
    }
    if (sortBy === "title") {
      return (a.title || a.originalFilename || "").localeCompare(
        b.title || b.originalFilename || "",
        undefined,
        { sensitivity: "base", numeric: true },
      );
    }
    return (Date.parse(b.uploadedAt) || 0) - (Date.parse(a.uploadedAt) || 0);
  });
}
export function validateUpload(file) {
  if (!file) return "Please select a document first.";
  if (!/\.(pdf|docx|txt)$/i.test(file.name)) return "Only PDF, DOCX and TXT files are supported.";
  if (file.size === 0) return "Please choose a non-empty document.";
  if (file.size > 10 * 1024 * 1024) return "Maximum allowed file size is 10 MB.";
  return "";
}
