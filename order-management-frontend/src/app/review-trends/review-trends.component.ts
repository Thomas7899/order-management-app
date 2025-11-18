// order-management-frontend/src/app/review-trends/review-trends.component.ts
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  ReviewTrendsService,
  ProductTrendReport,
  CategoryTrendReport,
  AnomalyReport,
  GlobalTrendReport
} from '../services/review-trends.service';

type TrendMode = 'global' | 'product' | 'category' | 'anomalies';

@Component({
  selector: 'app-review-trends',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './review-trends.component.html',
  styleUrls: ['./review-trends.component.css']
})
export class ReviewTrendsComponent {
  mode: TrendMode = 'global';
  loading = false;
  error: string | null = null;

  startDate = '';
  endDate = '';
  productId: number | null = null;
  category: string = 'Elektronik';

  globalTrend: GlobalTrendReport | null = null;
  productTrend: ProductTrendReport | null = null;
  categoryTrend: CategoryTrendReport | null = null;
  anomalyReport: AnomalyReport | null = null;

  constructor(private trendsService: ReviewTrendsService) {
    this.setDefaultDates();
  }

  setDefaultDates(): void {
    const today = new Date();
    const past = new Date();
    past.setDate(today.getDate() - 30);
    this.startDate = past.toISOString().slice(0, 10);
    this.endDate = today.toISOString().slice(0, 10);
  }

  setMode(newMode: TrendMode): void {
    this.mode = newMode;
    this.error = null;
    this.globalTrend = null;
    this.productTrend = null;
    this.categoryTrend = null;
    this.anomalyReport = null;
  }

  analyze(): void {
    if (!this.startDate || !this.endDate) {
      this.error = 'Bitte Start- und Enddatum wählen.';
      return;
    }

    this.error = null;
    this.loading = true;

    this.globalTrend = null;
    this.productTrend = null;
    this.categoryTrend = null;
    this.anomalyReport = null;

    if (this.mode === 'global') {
      this.trendsService
        .getGlobalTrend(this.startDate, this.endDate)
        .subscribe({
          next: (data) => {
            this.globalTrend = data;
            this.loading = false;
          },
          error: () => {
            this.error = 'Globale Trend-Analyse konnte nicht geladen werden.';
            this.loading = false;
          }
        });
    }
    
    else if (this.mode === 'product') {
      if (!this.productId) {
        this.error = 'Bitte eine Produkt-ID angeben.';
        this.loading = false;
        return;
      }

      this.trendsService
        .getProductTrend(this.productId, this.startDate, this.endDate)
        .subscribe({
          next: (data) => {
            this.productTrend = data;
            this.loading = false;
          },
          error: () => {
            this.error = 'Trend-Analyse für Produkt konnte nicht geladen werden.';
            this.loading = false;
          }
        });
    }
    
    else if (this.mode === 'category') {
      if (!this.category.trim()) {
        this.error = 'Bitte eine Kategorie wählen.';
        this.loading = false;
        return;
      }

      this.trendsService
        .getCategoryTrend(this.category.trim(), this.startDate, this.endDate)
        .subscribe({
          next: (data) => {
            this.categoryTrend = data;
            this.loading = false;
          },
          error: () => {
            this.error = 'Trend-Analyse für Kategorie konnte nicht geladen werden.';
            this.loading = false;
          }
        });
    }
    
    else if (this.mode === 'anomalies') {
      this.trendsService
        .getAnomalies(this.startDate, this.endDate)
        .subscribe({
          next: (data) => {
            this.anomalyReport = data;
            this.loading = false;
          },
          error: () => {
            this.error = 'Anomalie-Analyse konnte nicht geladen werden.';
            this.loading = false;
          }
        });
    }
  }

  formatDate(date: string): string {
    if (!date) return 'N/A';
    const d = new Date(date);
    if (isNaN(d.getTime())) return date;

    return d.toLocaleDateString('de-DE', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    });
  }

  formatDateTime(dateString: string): string {
    if (!dateString) return 'N/A';
    return new Date(dateString).toLocaleString('de-DE', {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
    });
  }
}