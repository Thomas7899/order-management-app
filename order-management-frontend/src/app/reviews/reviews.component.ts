import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReviewsService, Review } from '../services/reviews.service';

@Component({
  selector: 'app-reviews',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reviews.component.html',
  styleUrls: ['./reviews.component.css']
})
export class ReviewsComponent {
  query = '';
  reviews: Review[] = [];
  loading = false;
  error: string | null = null;

  avgRating = 0;
  sentiment = 'neutral';
  topWords: string[] = [];

  constructor(private reviewsService: ReviewsService) {}

  search(): void {
    if (!this.query.trim()) return;

    this.loading = true;
    this.error = null;
    this.reviews = [];

    this.reviewsService.getSimilarReviews(this.query).subscribe({
      next: (data) => {
        this.reviews = data;
        this.calculateInsights();
        this.loading = false;
      },
      error: () => {
        this.error = 'Fehler beim Laden der Bewertungen';
        this.loading = false;
      }
    });
  }

  calculateInsights(): void {
    if (this.reviews.length === 0) return;

    const ratings = this.reviews.map(r => r.rating);
    this.avgRating = Number((ratings.reduce((a, b) => a + b, 0) / ratings.length).toFixed(1));

    this.sentiment = this.avgRating >= 4 ? 'positiv' : this.avgRating >= 2.5 ? 'neutral' : 'negativ';

    const wordCount: Record<string, number> = {};
    this.reviews.forEach(r => {
      r.comment.toLowerCase().split(/\s+/).forEach(word => {
        if (word.length > 3 && /^[a-zäöüß]+$/i.test(word)) {
          wordCount[word] = (wordCount[word] || 0) + 1;
        }
      });
    });

    this.topWords = Object.entries(wordCount)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 5)
      .map(([word]) => word);
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleDateString('de-DE', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
}
