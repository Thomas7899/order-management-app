// order-management-frontend/src/app/review-trends/review-trends.component.ts
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgxEchartsModule } from 'ngx-echarts';
import {
  ReviewTrendsService,
  ProductTrendReport,
  CategoryTrendReport,
  AnomalyReport,
  GlobalTrendReport,
  EnhancedSentimentReport,
  ThemeCluster,
  CategorizedReview
} from '../services/review-trends.service';

type TrendMode = 'global' | 'product' | 'category' | 'anomalies' | 'sentiment';

@Component({
  selector: 'app-review-trends',
  standalone: true,
  imports: [CommonModule, FormsModule, NgxEchartsModule],
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
  
  // Enhanced Sentiment Data
  sentimentReport: EnhancedSentimentReport | null = null;
  sentimentSubTab: 'overview' | 'emotions' | 'themes' | 'critical' = 'overview';
  
  // Charts
  emotionChartOptions: any = {};
  themeChartOptions: any = {};
  sentimentTrendChartOptions: any = {};

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
    this.sentimentReport = null;
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
    this.sentimentReport = null;

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
    
    else if (this.mode === 'sentiment') {
      this.trendsService
        .getEnhancedSentimentReport(this.startDate, this.endDate)
        .subscribe({
          next: (data) => {
            this.sentimentReport = data;
            this.loading = false;
            this.buildSentimentCharts();
          },
          error: () => {
            this.error = 'Erweiterte Sentiment-Analyse konnte nicht geladen werden.';
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

  // === Enhanced Sentiment Methods ===

  setSentimentSubTab(tab: 'overview' | 'emotions' | 'themes' | 'critical'): void {
    this.sentimentSubTab = tab;
  }

  private buildSentimentCharts(): void {
    if (!this.sentimentReport) return;
    this.buildEmotionChart();
    this.buildThemeChart();
    this.buildSentimentTrendChart();
  }

  private buildEmotionChart(): void {
    const emotions = this.sentimentReport!.emotionDistribution;
    const data = Object.entries(emotions).map(([name, value]) => ({
      name: this.getEmotionLabel(name),
      value,
      itemStyle: { color: this.trendsService.getEmotionColor(name) }
    }));

    this.emotionChartOptions = {
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}\n{d}%' },
        data
      }]
    };
  }

  private buildThemeChart(): void {
    const themes = this.sentimentReport!.themeDistribution;
    const entries = Object.entries(themes).sort((a, b) => b[1] - a[1]);

    this.themeChartOptions = {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      xAxis: {
        type: 'category',
        data: entries.map(([name]) => name),
        axisLabel: { rotate: 45, color: '#94a3b8' }
      },
      yAxis: { type: 'value', axisLabel: { color: '#94a3b8' } },
      series: [{
        type: 'bar',
        data: entries.map(([name, value]) => ({
          value,
          itemStyle: { 
            color: this.getThemeColor(name),
            borderRadius: [4, 4, 0, 0]
          }
        })),
        label: { show: true, position: 'top' }
      }]
    };
  }

  private buildSentimentTrendChart(): void {
    const trends = this.sentimentReport!.sentimentTrends;
    if (!trends || trends.length === 0) return;

    this.sentimentTrendChartOptions = {
      tooltip: { trigger: 'axis' },
      legend: { data: ['Positiv', 'Neutral', 'Negativ'], textStyle: { color: '#94a3b8' } },
      xAxis: {
        type: 'category',
        data: trends.map(t => this.formatDate(t.date)),
        axisLabel: { color: '#94a3b8' }
      },
      yAxis: { type: 'value', axisLabel: { color: '#94a3b8' } },
      series: [
        {
          name: 'Positiv',
          type: 'line',
          stack: 'Total',
          smooth: true,
          areaStyle: { opacity: 0.3 },
          data: trends.map(t => t.positiveCount),
          itemStyle: { color: '#28a745' }
        },
        {
          name: 'Neutral',
          type: 'line',
          stack: 'Total',
          smooth: true,
          areaStyle: { opacity: 0.3 },
          data: trends.map(t => t.neutralCount),
          itemStyle: { color: '#6c757d' }
        },
        {
          name: 'Negativ',
          type: 'line',
          stack: 'Total',
          smooth: true,
          areaStyle: { opacity: 0.3 },
          data: trends.map(t => t.negativeCount),
          itemStyle: { color: '#dc3545' }
        }
      ]
    };
  }

  // Helper Methods
  getEmotionEmoji(emotion: string): string {
    return this.trendsService.getEmotionEmoji(emotion);
  }

  getEmotionColor(emotion: string): string {
    return this.trendsService.getEmotionColor(emotion);
  }

  getSentimentColor(sentiment: string): string {
    return this.trendsService.getSentimentColor(sentiment);
  }

  getCategoryIcon(category: string): string {
    return this.trendsService.getCategoryIcon(category);
  }

  getUrgencyColor(urgency: string): string {
    return this.trendsService.getUrgencyColor(urgency);
  }

  private getEmotionLabel(emotion: string): string {
    const labels: Record<string, string> = {
      JOY: 'Freude',
      SATISFACTION: 'Zufriedenheit',
      SURPRISE: 'Überraschung',
      FRUSTRATION: 'Frustration',
      DISAPPOINTMENT: 'Enttäuschung',
      ANGER: 'Ärger'
    };
    return labels[emotion] || emotion;
  }

  private getThemeColor(theme: string): string {
    const colors: Record<string, string> = {
      QUALITÄT: '#3b82f6',
      LIEFERUNG: '#10b981',
      PREIS: '#f59e0b',
      SERVICE: '#8b5cf6',
      BENUTZERFREUNDLICHKEIT: '#ec4899',
      DESIGN: '#06b6d4',
      FUNKTIONALITÄT: '#84cc16',
      SONSTIGES: '#6b7280'
    };
    return colors[theme] || '#6b7280';
  }

  getScoreClass(score: number): string {
    if (score > 0.3) return 'positive';
    if (score < -0.3) return 'negative';
    return 'neutral';
  }

  getBusinessImpactColor(impact: string): string {
    const colors: Record<string, string> = {
      HIGH: '#dc3545',
      MEDIUM: '#ffc107',
      LOW: '#28a745'
    };
    return colors[impact] || '#6c757d';
  }

  // Additional Helper Methods for Sentiment Template
  getEmotionEntries(): { key: string; value: number }[] {
    if (!this.sentimentReport?.emotionDistribution) return [];
    return Object.entries(this.sentimentReport.emotionDistribution).map(([key, value]) => ({ key, value }));
  }

  getEmotionIcon(emotion: string): string {
    const icons: Record<string, string> = {
      JOY: '😊',
      SATISFACTION: '😌',
      SURPRISE: '😲',
      FRUSTRATION: '😤',
      DISAPPOINTMENT: '😞',
      ANGER: '😠',
      NEUTRAL: '😐',
      joy: '😊',
      satisfaction: '😌',
      surprise: '😲',
      frustration: '😤',
      disappointment: '😞',
      anger: '😠',
      neutral: '😐'
    };
    return icons[emotion] || '🤔';
  }

  getSentimentClass(score: number): string {
    if (score > 0.3) return 'positive';
    if (score < -0.3) return 'negative';
    return 'neutral';
  }

  // Berechne Gesamtzahl der Reviews aus den Sentiment-Trends
  getTotalReviews(): number {
    if (!this.sentimentReport?.sentimentTrends) return 0;
    return this.sentimentReport.sentimentTrends.reduce((sum, trend) => 
      sum + trend.positiveCount + trend.neutralCount + trend.negativeCount, 0);
  }

  // Berechne positive Prozent aus Trends
  getPositivePercentage(): number {
    if (!this.sentimentReport?.sentimentTrends) return 0;
    const total = this.getTotalReviews();
    if (total === 0) return 0;
    const positiveSum = this.sentimentReport.sentimentTrends.reduce((sum, trend) => 
      sum + trend.positiveCount, 0);
    return (positiveSum / total) * 100;
  }

  // Berechne neutrale Prozent aus Trends
  getNeutralPercentage(): number {
    if (!this.sentimentReport?.sentimentTrends) return 0;
    const total = this.getTotalReviews();
    if (total === 0) return 0;
    const neutralSum = this.sentimentReport.sentimentTrends.reduce((sum, trend) => 
      sum + trend.neutralCount, 0);
    return (neutralSum / total) * 100;
  }

  // Berechne negative Prozent aus Trends
  getNegativePercentage(): number {
    if (!this.sentimentReport?.sentimentTrends) return 0;
    const total = this.getTotalReviews();
    if (total === 0) return 0;
    const negativeSum = this.sentimentReport.sentimentTrends.reduce((sum, trend) => 
      sum + trend.negativeCount, 0);
    return (negativeSum / total) * 100;
  }

  // Trend-Icon basierend auf Richtung
  getTrendIcon(direction: string): string {
    const icons: Record<string, string> = {
      IMPROVING: '📈',
      STABLE: '➡️',
      DECLINING: '📉'
    };
    return icons[direction] || '➡️';
  }
}