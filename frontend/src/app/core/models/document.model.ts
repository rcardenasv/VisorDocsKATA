// Represents a document upload response
export interface UploadResponse {
  documentId: string;
  status: string;
}

// Document status event emitted via SSE
export interface DocumentStatusEvent {
  documentId: string;
  status: 'PROCESSING' | 'INDEXED' | 'ERROR';
  errorMessage?: string;
}

// Metadata nested inside DocumentDetail
export interface DocumentMetadata {
  title: string;
  author: string;
  category: string;
  tags: string[];
  version: string;
}

// Full document detail response
export interface DocumentDetail {
  documentId: string;
  status: 'PROCESSING' | 'INDEXED' | 'ERROR';
  metadata: DocumentMetadata;
  content: string;
  originalFileName: string;
  fileType: string;
  createdAt: string;
  updatedAt: string;
  errorMessage?: string;
}

// A single item in a search result list
export interface SearchItem {
  documentId: string;
  title: string;
  metadata: DocumentMetadata;
  highlight: string[];
}

// Search response with pagination
export interface SearchResponse {
  items: SearchItem[];
  page: number;
  pageSize: number;
  total: number;
}
