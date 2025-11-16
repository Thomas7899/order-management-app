// order-management-frontend/src/app/review-trend/review-trends.component.ts
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReviewTrendService, ReviewTrendReport } from '../services/review-trend.service';

@Component({
    selector: 'app-review-trends',
    imports: [CommonModule, FormsModule],
    templateUrl: './legacy-review-trend.component.html',
    styleUrls: ['./legacy-review-trend.component.css']
})
export class LegacyReviewTrendComponent {
  from = '';
  to = '';
  reports: ReviewTrendReport[] = [];
  loading = false;
  error: string | null = null;

  constructor(private trendService: ReviewTrendService) {
    this.loadReports();
  }

  loadReports(): void {
    this.loading = true;
    this.trendService.listAll().subscribe({
      next: (data) => {
        this.reports = data.sort(
          (a, b) => new Date(b.generatedAt).getTime() - new Date(a.generatedAt).getTime()
        );
        this.loading = false;
      },
      error: () => {
        this.error = 'Fehler beim Laden der Trendberichte';
        this.loading = false;
      }
    });
  }

  analyze(): void {
    if (!this.from || !this.to) {
      this.error = 'Bitte beide Datumsfelder ausfüllen.';
      return;
    }

    this.loading = true;
    this.trendService.analyze(this.from, this.to).subscribe({
      next: (report) => {
        this.reports.unshift(report);
        this.loading = false;
      },
      error: () => {
        this.error = 'Fehler bei der Analyse.';
        this.loading = false;
      }
    });
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleString('de-DE', {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
    });
  }
}
