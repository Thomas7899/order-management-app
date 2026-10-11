import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_URL, toParams } from './api';
import { SILENT_ERRORS } from './http.interceptors';
import { CodeText } from './models';

/** KI = Text vom Sprachmodell, REGELN = aus den Kennzahlen formuliert (keine KI verfügbar). */
export type NarrativeSource = 'KI' | 'REGELN';
export type ThemeDirection = 'IMPROVING' | 'STABLE' | 'DECLINING' | 'NEW';

export interface PeriodStats {
  reviews: number;
  avgRating: number;
  sentimentScore: number;
  positiveShare: number;
  negativeShare: number;
}

export interface ThemeTrend {
  theme: CodeText;
  mentions: number;
  previousMentions: number;
  positiveShare: number;
  negativeShare: number;
  previousNegativeShare: number;
  direction: ThemeDirection;
}

export interface ProductMovement {
  productId: number;
  productName: string;
  category?: string;
  reviews: number;
  avgRating: number;
  previousReviews: number;
  previousAvgRating: number | null;
  delta: number | null;
}

export interface ReviewQuote {
  reviewId: number;
  productId: number;
  productName: string;
  rating: number;
  createdAt: string;
  comment: string;
  sentiment: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE';
  themes: string[];
}

export interface Count {
  code: string;
  text: string;
  count: number;
}

export interface ClassificationJob {
  running: boolean;
  processed: number;
  total: number;
  failedBatches: number;
  startedAt?: string;
  finishedAt?: string;
  lastError?: string;
}

export interface Classification {
  reviews: number;
  byAi: number;
  byRules: number;
  aiAvailable: boolean;
  job: ClassificationJob;
}

export interface TrendReport {
  scope: 'GLOBAL' | 'PRODUCT' | 'CATEGORY';
  title: string;
  windowStart: string;
  windowEnd: string;
  previousStart: string;
  previousEnd: string;
  current: PeriodStats;
  previous: PeriodStats;
  themes: ThemeTrend[];
  declining: ProductMovement[];
  improving: ProductMovement[];
  positiveTrends: string[];
  negativeTrends: string[];
  observations: string[];
  summary: string;
  recommendations: string[];
  narrativeSource: NarrativeSource;
  evidence: ReviewQuote[];
}

export interface ProductAnomaly {
  productId: number;
  productName: string;
  category?: string;
  severity: 'HIGH' | 'MEDIUM' | 'LOW';
  reviews: number;
  avgRating: number;
  baselineReviews: number;
  baselineAvgRating: number | null;
  negativeShare: number;
  topProblems: string[];
  assessment: string;
  recommendedAction: string;
  evidence: ReviewQuote[];
}

export interface ThemeAnomaly {
  theme: CodeText;
  negativeMentions: number;
  negativePerWeek: number;
  baselineNegativePerWeek: number;
  negativeShare: number;
  baselineNegativeShare: number;
  assessment: string;
}

export interface AnomalyReport {
  windowStart: string;
  windowEnd: string;
  baselineStart: string;
  products: ProductAnomaly[];
  themes: ThemeAnomaly[];
  narrativeSource: NarrativeSource;
}

export interface SentimentPoint {
  periodStart: string;
  reviews: number;
  avgScore: number;
  positive: number;
  neutral: number;
  negative: number;
}

export interface SentimentReport {
  windowStart: string;
  windowEnd: string;
  previousStart: string;
  previousEnd: string;
  current: PeriodStats;
  previous: PeriodStats;
  sentiments: Count[];
  emotions: Count[];
  themes: ThemeTrend[];
  timeline: SentimentPoint[];
  timelineGranularity: 'DAY' | 'WEEK';
  criticalReviews: ReviewQuote[];
  classification: Classification;
}

export interface Scope {
  productId?: number | null;
  category?: string | null;
}

/** Auswertung der Kundenbewertungen; Fehler zeigt die Seite selbst an. */
@Injectable({ providedIn: 'root' })
export class ReviewInsightsService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_URL}/reviews/insights`;
  private readonly silent = { context: new HttpContext().set(SILENT_ERRORS, true) };

  trend(start: string, end: string, scope: Scope = {}): Observable<TrendReport> {
    return this.http.get<TrendReport>(`${this.url}/trend`, { ...this.silent, params: toParams({ start, end, ...scope }) });
  }

  anomalies(start: string, end: string): Observable<AnomalyReport> {
    return this.http.get<AnomalyReport>(`${this.url}/anomalies`, { ...this.silent, params: toParams({ start, end }) });
  }

  sentiment(start: string, end: string, scope: Scope = {}): Observable<SentimentReport> {
    return this.http.get<SentimentReport>(`${this.url}/sentiment`, { ...this.silent, params: toParams({ start, end, ...scope }) });
  }

  classification(): Observable<Classification> {
    return this.http.get<Classification>(`${this.url}/classification`, this.silent);
  }

  startClassification(): Observable<Classification> {
    return this.http.post<Classification>(`${this.url}/classification`, null);
  }
}
