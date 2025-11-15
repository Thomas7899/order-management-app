// order-management-frontend/src/app/services/reviews.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

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

  private apiUrl = `${environment.apiUrl}/reviews`;

  constructor(private http: HttpClient) {}

  getSimilarReviews(query: string, limit: number): Observable<Review[]> {
    return this.http.get<Review[]>(`${this.apiUrl}/similar`, {
      params: { query, limit }
    });
  }

  createReview(review: Partial<Review>): Observable<Review> {
    return this.http.post<Review>(this.apiUrl, review);
  }
}
