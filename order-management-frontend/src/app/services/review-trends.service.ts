// order-management-frontend/src/app/services/review-trends.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface GlobalTrendReport {
  id: string;
  summary: string;
  positiveTrends: string[];
  negativeTrends: string[];
  neutralObservations: string[];
  windowStart: string;
  windowEnd: string;
  generatedAt: string;
}

export interface ProductTrendReport {
  productId: number;
  productName: string;
  reviewCount: number;
  avgRating: number;
  summary: string;
  positiveTrends: string[];
  negativeTrends: string[];
  neutralObservations: string[];
  windowStart: string;
  windowEnd: string;
}

export interface CategoryTrendReport {
  category: string;
  reviewCount: number;
  avgRating: number;
  summary: string;
  positiveTrends: string[];
  negativeTrends: string[];
  neutralObservations: string[];
  windowStart: string;
  windowEnd: string;
}

// NEU: Ein Interface für eine einzelne, strukturierte Anomalie
export interface ProductAnomaly {
  productId: number;
  productName: string;
  reason: string; // z.B. "Starker Bewertungsabfall" oder "Häufung negativer Keywords"
  avgRating: number;
  reviewCount: number;
  negativeKeywords: string[]; // z.B. ["defekt", "kaputt"]
}

// NEU: Das 'AnomalyReport'-Interface verwendet jetzt 'ProductAnomaly[]'
export interface AnomalyReport {
  anomalies: ProductAnomaly[]; // <-- HIER IST DIE ÄNDERUNG
  windowStart: string;
  windowEnd: string;
}

@Injectable({
  providedIn: 'root'
})
export class ReviewTrendsService {
  private baseUrl = `${environment.apiUrl}/api/reviews/trends`;
private productSearchUrl = `${environment.apiUrl}/api/products/search`;

  constructor(private http: HttpClient) {}

  getGlobalTrend(start: string, end: string): Observable<GlobalTrendReport> {
    let url = `${this.baseUrl}/analyze`;
    const params = [];
    if (start) params.push(`windowStart=${start}`);
    if (end) params.push(`windowEnd=${end}`);

    if (params.length) url += `?${params.join('&')}`;

    return this.http.post<GlobalTrendReport>(url, {});
  }

  getProductTrend(productId: number, start: string, end: string): Observable<ProductTrendReport> {
    return this.http.get<ProductTrendReport>(
      `${this.baseUrl}/product/${productId}?start=${start}&end=${end}`
    );
  }

  getCategoryTrend(category: string, start: string, end: string): Observable<CategoryTrendReport> {
    return this.http.get<CategoryTrendReport>(
      `${this.baseUrl}/category/${encodeURIComponent(category)}?start=${start}&end=${end}`
    );
  }

  getAnomalies(start: string, end: string): Observable<AnomalyReport> {
    return this.http.get<AnomalyReport>(
      `${this.baseUrl}/anomalies?start=${start}&end=${end}`
    );
  }

  searchProducts(query: string, limit: number = 10): Observable<{ id: number; name: string }[]> {
    return this.http.get<{ id: number; name: string }[]>(this.productSearchUrl, {
      params: { q: query, limit }
    });
  }
}