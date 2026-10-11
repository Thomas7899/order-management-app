import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URL } from './api';
import { SILENT_ERRORS } from './http.interceptors';

export interface Review {
  id: number;
  comment: string;
  rating: number;
  createdAt: string;
  productId: number | null;
  productName: string | null;
  productPrice: number | null;
  userId: number | null;
  userName: string | null;
  orderId: number | null;
  orderItemId: number | null;
}

@Injectable({ providedIn: 'root' })
export class ReviewsService {
  private readonly http = inject(HttpClient);

  /** Semantische Suche (Embeddings + pgvector). Fehler zeigt die Seite selbst an. */
  getSimilarReviews(query: string, limit: number): Observable<Review[]> {
    return this.http.get<Review[]>(`${API_URL}/reviews/similar`,
      { params: { query, limit }, context: new HttpContext().set(SILENT_ERRORS, true) });
  }

  forProduct(productId: number): Observable<Review[]> {
    return this.http.get<Review[]>(`${API_URL}/reviews/product/${productId}`);
  }
}
