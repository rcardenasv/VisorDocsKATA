import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DocumentStatusEvent } from '../models/document.model';

@Injectable({ providedIn: 'root' })
export class SseService {
  /**
   * Opens an SSE connection to the backend and returns an Observable
   * that emits DocumentStatusEvent objects. The connection is closed
   * automatically when the Observable is unsubscribed from.
   */
  observeDocument(documentId: string): Observable<DocumentStatusEvent> {
    return new Observable(observer => {
      const source = new EventSource(`/api/events?documentId=${documentId}`);

      source.onmessage = (event) => {
        try {
          observer.next(JSON.parse(event.data) as DocumentStatusEvent);
        } catch (e) {
          observer.error(e);
        }
      };

      source.onerror = (event) => {
        observer.error(event);
        source.close();
      };

      // Cleanup when unsubscribed
      return () => source.close();
    });
  }
}
