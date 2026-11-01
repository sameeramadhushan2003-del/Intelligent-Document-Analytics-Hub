export function formatBytes(bytes = 0) {
  return bytes < 1024 ? `${bytes} B` : bytes < 1048576 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1048576).toFixed(2)} MB`;
}
export function documentOverview(documents) {
  return { total: documents.length, extracted: documents.filter(d => ["EXTRACTED", "INDEXED"].includes(d.processingStatus)).length,
    pending: documents.filter(d => d.processingStatus === "UPLOADED").length,
    recent: [...documents].sort((a, b) => (Date.parse(b.uploadedAt) || 0) - (Date.parse(a.uploadedAt) || 0)).slice(0, 4) };
}
export function filterDocuments(documents, query, status) {
  return documents.filter(d => (!status || d.processingStatus === status) && d.originalFilename?.toLowerCase().includes(query.toLowerCase()));
}
export function validateUpload(file) {
  if (!file) return "Please select a document first.";
  if (!/\.(pdf|docx|txt)$/i.test(file.name)) return "Only PDF, DOCX and TXT files are supported.";
  if (file.size === 0) return "Please choose a non-empty document.";
  if (file.size > 10 * 1024 * 1024) return "Maximum allowed file size is 10 MB.";
  return "";
}
