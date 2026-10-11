import { CUSTOM_ELEMENTS_SCHEMA, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { NgxEchartsDirective } from 'ngx-echarts';
import type { EChartsOption } from 'echarts';
import { AuthService } from '../../core/auth.service';
import { MaterialsApi } from '../../core/master-data-api.service';
import { Material } from '../../core/models';
import {
  AnomalyReport, CategoryTrendReport, EnhancedSentimentReport, GlobalTrendReport, ProductTrendReport, ReviewTrendsService
} from '../../core/review-trends.service';
import { chartTheme } from '../../shared/charts';
import { todayIso } from '../../shared/status';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';

type Mode = 'global' | 'product' | 'category' | 'anomalies' | 'sentiment';

interface TrendResult {
  summary: string;
  positiveTrends: string[];
  negativeTrends: string[];
  neutralObservations: string[];
  windowStart: string;
  windowEnd: string;
  reviewCount?: number;
  avgRating?: number;
  title: string;
}

const EMOTIONS: Record<string, string> = {
  JOY: 'Freude', SATISFACTION: 'Zufriedenheit', SURPRISE: 'Überraschung',
  FRUSTRATION: 'Frust', DISAPPOINTMENT: 'Enttäuschung', ANGER: 'Ärger'
};

/** KI-gestützte Trend-, Anomalie- und Stimmungsanalyse der Kundenbewertungen. */
@Component({
  selector: 'om-review-trends',
  imports: [FormsModule, RouterLink, Ui5ValueAccessorDirective, NgxEchartsDirective, DatePipe, DecimalPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './review-trends.component.html',
  styles: `
    .trend-lists { display: grid; grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr)); gap: 1rem; }
    .trend-lists ul { margin: .5rem 0 0; padding-left: 1.25rem; }
    .trend-lists li { margin-bottom: .35rem; line-height: 1.4; }
    .summary { line-height: 1.5; margin: 0 0 .5rem; }
    .mode { margin-bottom: .75rem; }
  `
})
export class ReviewTrendsComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ReviewTrendsService);
  private readonly materialsApi = inject(MaterialsApi);

  protected readonly mode = signal<Mode>('global');
  protected startDate = '';
  protected endDate = todayIso();
  protected productId = '';
  protected category = '';

  protected readonly materials = signal<Material[]>([]);
  protected readonly categories = computed(() => [...new Set(this.materials().map(m => m.category).filter(Boolean) as string[])].sort());

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly trend = signal<TrendResult | null>(null);
  protected readonly generatedAt = signal<string | null>(null);
  protected readonly anomalies = signal<AnomalyReport | null>(null);
  protected readonly sentiment = signal<EnhancedSentimentReport | null>(null);

  protected readonly emotionChart = computed<EChartsOption | null>(() => {
    const s = this.sentiment();
    if (!s?.emotionDistribution) return null;
    const t = chartTheme();
    const data = Object.entries(s.emotionDistribution).map(([k, v]) => ({ name: EMOTIONS[k] ?? k, value: v }));
    return {
      ...t.base,
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, textStyle: { color: t.label } },
      series: [{ type: 'pie', radius: ['45%', '70%'], data, label: { color: t.text }, itemStyle: { borderRadius: 4 } }]
    };
  });

  protected readonly themeChart = computed<EChartsOption | null>(() => {
    const s = this.sentiment();
    if (!s?.themeDistribution) return null;
    const t = chartTheme();
    const entries = Object.entries(s.themeDistribution).sort((a, b) => a[1] - b[1]);
    return {
      ...t.base,
      grid: { left: 140, right: 24, top: 8, bottom: 24 },
      tooltip: { trigger: 'axis' },
      xAxis: { ...t.valueAxis, type: 'value' },
      yAxis: { ...t.categoryAxis, type: 'category', data: entries.map(e => e[0]) },
      series: [{ type: 'bar', data: entries.map(e => e[1]), itemStyle: { color: t.palette[0], borderRadius: [0, 4, 4, 0] } }]
    };
  });

  ngOnInit(): void {
    const start = new Date();
    start.setDate(start.getDate() - 30);
    this.startDate = start.toISOString().slice(0, 10);
    this.materialsApi.list().subscribe(m => this.materials.set(m.sort((a, b) => a.name.localeCompare(b.name))));
    this.api.getLatestGlobalTrend().subscribe({ next: r => r && this.showGlobal(r), error: () => undefined });
  }

  protected setMode(event: Event): void {
    const key = (event as CustomEvent<{ selectedItems: HTMLElement[] }>).detail.selectedItems[0]?.dataset['key'] as Mode;
    this.mode.set(key);
    this.trend.set(null);
    this.generatedAt.set(null);
    this.anomalies.set(null);
    this.sentiment.set(null);
    this.error.set(null);
  }

  protected impactDesign(impact: string): string {
    return { HIGH: 'Negative', MEDIUM: 'Critical', LOW: 'Neutral' }[impact] ?? 'Neutral';
  }

  protected impactText(impact: string): string {
    return { HIGH: 'Hoch', MEDIUM: 'Mittel', LOW: 'Niedrig' }[impact] ?? impact;
  }

  protected directionText(d: string): string {
    return { IMPROVING: 'Verbessernd', STABLE: 'Stabil', DECLINING: 'Verschlechternd' }[d] ?? d;
  }

  protected scoreDesign(score: number): string {
    return score >= 0.3 ? 'Positive' : score <= -0.3 ? 'Negative' : 'Neutral';
  }

  protected materialIdByName(name: string): number | undefined {
    return this.materials().find(m => m.name === name)?.id;
  }

  protected analyze(): void {
    this.error.set(null);
    this.trend.set(null);
    this.generatedAt.set(null);
    this.anomalies.set(null);
    this.sentiment.set(null);
    switch (this.mode()) {
      case 'global':
        this.run(this.api.getGlobalTrend(this.startDate, this.endDate), r => this.showGlobal(r));
        break;
      case 'product': {
        if (!this.productId) return this.error.set('Bitte ein Material auswählen.');
        this.run(this.api.getProductTrend(Number(this.productId), this.startDate, this.endDate), (r: ProductTrendReport) =>
          this.trend.set({ ...r, title: r.productName }));
        break;
      }
      case 'category': {
        if (!this.category) return this.error.set('Bitte eine Warengruppe auswählen.');
        this.run(this.api.getCategoryTrend(this.category, this.startDate, this.endDate), (r: CategoryTrendReport) =>
          this.trend.set({ ...r, title: `Warengruppe ${r.category}` }));
        break;
      }
      case 'anomalies':
        this.run(this.api.getAnomalies(this.startDate, this.endDate), r => this.anomalies.set(r));
        break;
      case 'sentiment':
        this.run(this.api.getEnhancedSentimentReport(this.startDate, this.endDate), r => this.sentiment.set(r));
        break;
    }
  }

  protected canAnalyzeGlobal(): boolean {
    return this.mode() !== 'global' || this.auth.hasAnyRole('CONTROLLING');
  }

  private showGlobal(r: GlobalTrendReport): void {
    this.trend.set({ ...r, title: 'Alle Bewertungen' });
    this.generatedAt.set(r.generatedAt);
  }

  private run<T>(request: Observable<T>, handle: (result: T) => void): void {
    this.loading.set(true);
    request.subscribe({
      next: result => {
        handle(result);
        this.loading.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(e.status === 403
          ? 'Für diese Analyse fehlt die Berechtigung (Rolle Controlling).'
          : 'Die KI-Analyse ist fehlgeschlagen. Ist im Backend ein gültiger OpenAI-API-Schlüssel konfiguriert?');
      }
    });
  }
}
