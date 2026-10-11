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
  customerId: number | null;
  customerName: string | null;
  orderId: number | null;
  orderItemId: number | null;
  sentiment: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE' | null;
  themes: string[];
  replyText: string | null;
  repliedAt: string | null;
  repliedBy: string | null;
}

export interface OpenCritical {
  count: number;
  reviews: Review[];
}

export interface ReplySuggestion {
  text: string;
  /** KI = Vorschlag vom Sprachmodell, REGELN = Vorlage ohne KI */
  source: 'KI' | 'REGELN';
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

  /** Kritische Bewertungen (1–2 Sterne) der letzten Tage ohne Antwort. */
  openCritical(days = 30): Observable<OpenCritical> {
    return this.http.get<OpenCritical>(`${API_URL}/reviews/open-critical`, { params: { days } });
  }

  suggestReply(reviewId: number): Observable<ReplySuggestion> {
    return this.http.post<ReplySuggestion>(`${API_URL}/reviews/${reviewId}/reply-suggestion`, null);
  }

  reply(reviewId: number, text: string): Observable<Review> {
    return this.http.put<Review>(`${API_URL}/reviews/${reviewId}/reply`, { text });
  }
}
