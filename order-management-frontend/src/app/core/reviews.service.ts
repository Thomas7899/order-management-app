import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URL } from './api';
import { SILENT_ERRORS } from './http.interceptors';

/** Treffer der semantischen Suche; gleichlautende Bewertungen sind zusammengefasst (duplicates). */
export interface SearchHit {
  reviewId: number;
  comment: string;
  rating: number;
  createdAt: string;
  productId: number;
  productName: string;
  customerName: string | null;
  orderId: number | null;
  similarity: number;
  duplicates: number;
  sentiment: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE' | null;
  themes: string[];
}

export interface ProductHits {
  productId: number;
  productName: string;
  hits: number;
  avgRating: number;
}

export interface SearchResult {
  query: string;
  minSimilarity: number;
  totalHits: number;
  hits: SearchHit[];
  products: ProductHits[];
}

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
  search(query: string, limit: number): Observable<SearchResult> {
    return this.http.get<SearchResult>(`${API_URL}/reviews/similar`,
      { params: { query, limit }, context: new HttpContext().set(SILENT_ERRORS, true) });
  }

  forProduct(productId: number): Observable<Review[]> {
    return this.http.get<Review[]>(`${API_URL}/reviews/product/${productId}`);
  }
}
