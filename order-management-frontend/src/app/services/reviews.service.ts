import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Review {
  id: number;
  comment: string;
  rating: number;
  createdAt: string;
  productId?: number;
  productName?: string;
  productPrice?: number;
}

@Injectable({ providedIn: 'root' })
export class ReviewsService {
  private apiUrl = `${environment.apiUrl.replace('/api', '')}/reviews`;

  constructor(private http: HttpClient) {}

  getSimilarReviews(query: string): Observable<Review[]> {
    return this.http.get<Review[]>(`${this.apiUrl}/similar?query=${encodeURIComponent(query)}`);
  }
}
