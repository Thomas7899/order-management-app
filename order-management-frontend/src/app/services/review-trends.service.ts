// order-management-frontend/src/app/services/review-trends.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ProductTrendReport {
  productId: number;
  productName: string;
  summary: string;
  positiveTrends: string[];
  negativeTrends: string[];
  neutralObservations: string[];
  avgRating: number;
  reviewCount: number;
  windowStart: string;
  windowEnd: string;
}

export interface CategoryTrendReport {
  category: string;
  summary: string;
  positiveTrends: string[];
  negativeTrends: string[];
  neutralObservations: string[];
  avgRating: number;
  reviewCount: number;
  windowStart: string;
  windowEnd: string;
}

export interface AnomalyReport {
  anomalies: string[];
  windowStart: string;
  windowEnd: string;
}

@Injectable({ providedIn: 'root' })
export class ReviewTrendsService {
  private baseUrl = `${environment.apiUrl}/reviews/trends`;

  // 🔍 Produkt-Suche hat in vielen APIs einen eigenen Endpoint:
  private productSearchUrl = `${environment.apiUrl}/products/search`;

  constructor(private http: HttpClient) {}

  /** 📈 Produkt-Trend */
  getProductTrend(productId: number, start: string, end: string): Observable<ProductTrendReport> {
    return this.http.get<ProductTrendReport>(
      `${this.baseUrl}/product/${productId}?start=${start}&end=${end}`
    );
  }

  /** 📊 Kategorie-Trend */
  getCategoryTrend(category: string, start: string, end: string): Observable<CategoryTrendReport> {
    return this.http.get<CategoryTrendReport>(
      `${this.baseUrl}/category/${encodeURIComponent(category)}?start=${start}&end=${end}`
    );
  }

  /** 🚨 Anomalien */
  getAnomalies(start: string, end: string): Observable<AnomalyReport> {
    return this.http.get<AnomalyReport>(
      `${this.baseUrl}/anomalies?start=${start}&end=${end}`
    );
  }

  /** 🔍 Server-Side Produkt-Suche (autocomplete) */
  searchProducts(query: string, limit: number = 10): Observable<{ id: number; name: string }[]> {
    return this.http.get<{ id: number; name: string }[]>(this.productSearchUrl, {
      params: { q: query, limit }
    });
  }
}
