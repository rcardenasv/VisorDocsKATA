import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SearchResponse } from '../models/document.model';

@Injectable({ providedIn: 'root' })
export class SearchService {
  private readonly baseUrl = '/api/documents/search';

  constructor(private http: HttpClient) {}

  searchDocuments(query: string, page = 0, pageSize = 20): Observable<SearchResponse> {
    const params = new HttpParams()
      .set('q', query)
      .set('page', page.toString())
      .set('pageSize', pageSize.toString());

    return this.http.get<SearchResponse>(this.baseUrl, { params });
  }
}
