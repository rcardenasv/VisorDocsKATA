import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DocumentDetail, UploadResponse } from '../models/document.model';

@Injectable({ providedIn: 'root' })
export class DocumentService {
  private readonly baseUrl = '/api/documents';

  constructor(private http: HttpClient) {}

  uploadDocument(formData: FormData): Observable<UploadResponse> {
    return this.http.post<UploadResponse>(this.baseUrl, formData);
  }

  getDocument(id: string): Observable<DocumentDetail> {
    return this.http.get<DocumentDetail>(`${this.baseUrl}/${id}`);
  }
}
