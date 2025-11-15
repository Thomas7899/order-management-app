// order-management-frontend/src/app/services/review-trend.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ReviewTrendReport {
  id: number;
  windowStart: string;
  windowEnd: string;
  generatedAt: string;
  summary: string;
  positiveTrends: string[];
  negativeTrends: string[];
  neutralObservations: string[];
}

@Injectable({ providedIn: 'root' })
export class ReviewTrendService {
  private readonly baseUrl = `${environment.apiUrl}/reviews/trends`;

  constructor(private http: HttpClient) {}

  listAll(): Observable<ReviewTrendReport[]> {
    return this.http.get<ReviewTrendReport[]>(this.baseUrl);
  }

  analyze(windowStart?: string, windowEnd?: string): Observable<ReviewTrendReport> {
    let url = `${this.baseUrl}/analyze`;
    const params = [];

    if (windowStart) params.push(`windowStart=${windowStart}`);
    if (windowEnd) params.push(`windowEnd=${windowEnd}`);

    if (params.length) url += `?${params.join('&')}`;

    return this.http.post<ReviewTrendReport>(url, {});
  }
}
