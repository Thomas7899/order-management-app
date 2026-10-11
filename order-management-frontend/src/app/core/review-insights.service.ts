import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URL, toParams } from './api';
import { SILENT_ERRORS } from './http.interceptors';

interface TrendLists {
  summary: string;
  positiveTrends: string[];
  negativeTrends: string[];
  neutralObservations: string[];
  windowStart: string;
  windowEnd: string;
}

export interface GlobalTrendReport extends TrendLists {
  id: number;
  generatedAt: string;
}

export interface ProductTrendReport extends TrendLists {
  productId: number;
  productName: string;
  reviewCount: number;
  avgRating: number;
}

export interface CategoryTrendReport extends TrendLists {
  category: string;
  reviewCount: number;
  avgRating: number;
}

export interface ProductAnomaly {
  productId: number;
  productName: string;
  reason: string;
  avgRating: number;
  reviewCount: number;
  negativeKeywords: string[];
}

export interface AnomalyReport {
  anomalies: ProductAnomaly[];
  windowStart: string;
  windowEnd: string;
}

export interface CategorizedReview {
  reviewId: number;
  comment: string;
  rating: number;
  productName: string;
  primaryCategory: string;
  secondaryCategories: string[];
  extractedKeywords: string[];
  actionableInsight?: string;
}

export interface ThemeCluster {
  themeName: string;
  themeDescription: string;
  reviewCount: number;
  averageRating: number;
  overallSentiment: string;
  topKeywords: string[];
  sampleReviewIds: number[];
  trendDirection: 'IMPROVING' | 'STABLE' | 'DECLINING';
  businessImpact: 'HIGH' | 'MEDIUM' | 'LOW';
}

export interface EnhancedSentimentReport {
  windowStart: string;
  windowEnd: string;
  executiveSummary: string;
  overallSentimentScore: number;
  emotionDistribution: Record<string, number>;
  themeDistribution: Record<string, number>;
  themeClusters: ThemeCluster[];
  criticalReviews: CategorizedReview[];
  priorityActions: string[];
  positiveHighlights: string[];
  areasForImprovement: string[];
  sentimentChangeVsPreviousPeriod: number;
  performanceTrend: string;
}

/** KI-Auswertungen der Kundenbewertungen. Fehler zeigt die Seite selbst an. */
@Injectable({ providedIn: 'root' })
export class ReviewTrendsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_URL}/reviews/trends`;
  private readonly silent = { context: new HttpContext().set(SILENT_ERRORS, true) };

  /** Zuletzt gespeicherte Gesamtanalyse (204 ohne Inhalt, wenn noch keine existiert). */
  getLatestGlobalTrend(): Observable<GlobalTrendReport | null> {
    return this.http.get<GlobalTrendReport | null>(`${this.base}/latest`, this.silent);
  }

  getGlobalTrend(start: string, end: string): Observable<GlobalTrendReport> {
    return this.http.post<GlobalTrendReport>(`${this.base}/analyze`, null,
      { ...this.silent, params: toParams({ windowStart: start, windowEnd: end }) });
  }

  getProductTrend(productId: number, start: string, end: string): Observable<ProductTrendReport> {
    return this.http.get<ProductTrendReport>(`${this.base}/product/${productId}`, { ...this.silent, params: toParams({ start, end }) });
  }

  getCategoryTrend(category: string, start: string, end: string): Observable<CategoryTrendReport> {
    return this.http.get<CategoryTrendReport>(`${this.base}/category/${encodeURIComponent(category)}`,
      { ...this.silent, params: toParams({ start, end }) });
  }

  getAnomalies(start: string, end: string): Observable<AnomalyReport> {
    return this.http.get<AnomalyReport>(`${this.base}/anomalies`, { ...this.silent, params: toParams({ start, end }) });
  }

  getEnhancedSentimentReport(startDate: string, endDate: string): Observable<EnhancedSentimentReport> {
    return this.http.get<EnhancedSentimentReport>(`${API_URL}/reviews/sentiment/report`,
      { ...this.silent, params: toParams({ startDate, endDate }) });
  }
}
