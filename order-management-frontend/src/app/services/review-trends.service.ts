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

// === Enhanced Sentiment DTOs ===

export interface EnhancedSentiment {
  reviewId: number;
  sentiment: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE';
  sentimentScore: number;
  primaryEmotion: 'JOY' | 'SATISFACTION' | 'SURPRISE' | 'FRUSTRATION' | 'DISAPPOINTMENT' | 'ANGER';
  emotionIntensity: number;
  detectedThemes: string[];
  urgencyLevel: 'URGENT' | 'NORMAL' | 'LOW';
  requiresFollowUp: boolean;
  suggestedResponse?: string;
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

export interface SentimentTrend {
  date: string;
  averageSentiment: number;
  positiveCount: number;
  neutralCount: number;
  negativeCount: number;
  dominantEmotion: string;
  emergingThemes: string[];
}

export interface EnhancedSentimentReport {
  windowStart: string;
  windowEnd: string;
  executiveSummary: string;
  overallSentimentScore: number;
  emotionDistribution: { [key: string]: number };
  themeDistribution: { [key: string]: number };
  themeClusters: ThemeCluster[];
  sentimentTrends: SentimentTrend[];
  criticalReviews: CategorizedReview[];
  priorityActions: string[];
  positiveHighlights: string[];
  areasForImprovement: string[];
  sentimentChangeVsPreviousPeriod: number;
  performanceTrend: string;
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

  // === Enhanced Sentiment Methods ===

  getEnhancedSentimentReport(
    startDate: string,
    endDate: string,
    productId?: number,
    category?: string
  ): Observable<EnhancedSentimentReport> {
    let url = `${environment.apiUrl}/api/reviews/sentiment/report?startDate=${startDate}&endDate=${endDate}`;
    if (productId) url += `&productId=${productId}`;
    if (category) url += `&category=${encodeURIComponent(category)}`;
    return this.http.get<EnhancedSentimentReport>(url);
  }

  analyzeReviewSentiment(reviewId: number): Observable<EnhancedSentiment> {
    return this.http.get<EnhancedSentiment>(
      `${environment.apiUrl}/api/reviews/sentiment/${reviewId}`
    );
  }

  analyzeReviewsSentiment(reviewIds: number[]): Observable<EnhancedSentiment[]> {
    return this.http.post<EnhancedSentiment[]>(
      `${environment.apiUrl}/api/reviews/sentiment/batch`,
      reviewIds
    );
  }

  getCategorizedReviews(startDate: string, endDate: string): Observable<CategorizedReview[]> {
    return this.http.get<CategorizedReview[]>(
      `${environment.apiUrl}/api/reviews/sentiment/categorize?startDate=${startDate}&endDate=${endDate}`
    );
  }

  getThemeClusters(startDate: string, endDate: string): Observable<ThemeCluster[]> {
    return this.http.get<ThemeCluster[]>(
      `${environment.apiUrl}/api/reviews/sentiment/clusters?startDate=${startDate}&endDate=${endDate}`
    );
  }

  // === Sentiment Helper Methods ===

  getSentimentColor(sentiment: string): string {
    const colors: Record<string, string> = {
      POSITIVE: '#28a745',
      NEUTRAL: '#6c757d',
      NEGATIVE: '#dc3545'
    };
    return colors[sentiment] || '#6c757d';
  }

  getEmotionEmoji(emotion: string): string {
    const emojis: Record<string, string> = {
      JOY: '😊',
      SATISFACTION: '😌',
      SURPRISE: '😮',
      FRUSTRATION: '😤',
      DISAPPOINTMENT: '😞',
      ANGER: '😠'
    };
    return emojis[emotion] || '😐';
  }

  getEmotionColor(emotion: string): string {
    const colors: Record<string, string> = {
      JOY: '#28a745',
      SATISFACTION: '#7cb342',
      SURPRISE: '#17a2b8',
      FRUSTRATION: '#fd7e14',
      DISAPPOINTMENT: '#ffc107',
      ANGER: '#dc3545'
    };
    return colors[emotion] || '#6c757d';
  }

  getUrgencyColor(urgency: string): string {
    const colors: Record<string, string> = {
      URGENT: '#dc3545',
      NORMAL: '#ffc107',
      LOW: '#28a745'
    };
    return colors[urgency] || '#6c757d';
  }

  getCategoryIcon(category: string): string {
    const icons: Record<string, string> = {
      QUALITÄT: '⭐',
      LIEFERUNG: '📦',
      PREIS: '💰',
      SERVICE: '🎧',
      BENUTZERFREUNDLICHKEIT: '👆',
      DESIGN: '🎨',
      FUNKTIONALITÄT: '⚙️',
      SONSTIGES: '📋'
    };
    return icons[category] || '📋';
  }
}