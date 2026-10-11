import { CUSTOM_ELEMENTS_SCHEMA, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
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
  AnomalyReport, Classification, ReviewInsightsService, SentimentReport, ThemeDirection, TrendReport
} from '../../core/review-insights.service';
import { chartTheme } from '../../shared/charts';
import { todayIso } from '../../shared/status';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';

type Mode = 'trend' | 'anomalies' | 'sentiment';
type ScopeType = 'GLOBAL' | 'PRODUCT' | 'CATEGORY';

/**
 * Auswertung der Kundenbewertungen. Kennzahlen und Erkennung sind berechnet; Zusammenfassungen und
 * Einschätzungen formuliert bei verfügbarer KI das Sprachmodell aus diesen Fakten.
 */
@Component({
  selector: 'om-review-analysis',
  imports: [FormsModule, RouterLink, Ui5ValueAccessorDirective, NgxEchartsDirective, DatePipe, DecimalPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './review-analysis.component.html',
  styles: `
    .mode { margin-bottom: .75rem; }
    .presets { display: flex; gap: .25rem; align-items: flex-end; }
    .summary { line-height: 1.55; margin: 0 0 .5rem; font-size: var(--sapFontLargeSize); }
    .lists { display: grid; grid-template-columns: repeat(auto-fit, minmax(17rem, 1fr)); gap: 1rem; }
    .lists ul { margin: .5rem 0 0; padding-left: 1.25rem; }
    .lists li { margin-bottom: .35rem; line-height: 1.4; }
    .quotes { display: grid; grid-template-columns: repeat(auto-fill, minmax(19rem, 1fr)); gap: .75rem; }
    .quote { padding: .75rem 1rem; border-radius: .75rem; border: 1px solid var(--sapGroup_ContentBorderColor);
      background: var(--sapGroup_ContentBackground); display: flex; flex-direction: column; gap: .4rem; }
    .quote p { margin: 0; line-height: 1.45; }
    .quote .meta { display: flex; justify-content: space-between; align-items: center; gap: .5rem; }
    .tags { display: flex; gap: .25rem; flex-wrap: wrap; }
    .anomaly { display: flex; flex-direction: column; gap: .5rem; }
    .anomaly-head { display: flex; gap: .75rem; align-items: center; flex-wrap: wrap; }
    .delta-neg { color: var(--sapNegativeTextColor); }
    .delta-pos { color: var(--sapPositiveTextColor); }
    .source { display: flex; gap: .5rem; align-items: center; }
  `
})
export class ReviewAnalysisComponent implements OnInit, OnDestroy {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ReviewInsightsService);
  private readonly materialsApi = inject(MaterialsApi);

  protected readonly mode = signal<Mode>('trend');
  protected scopeType: ScopeType = 'GLOBAL';
  protected startDate = '';
  protected endDate = todayIso();
  protected productId = '';
  protected category = '';

  protected readonly materials = signal<Material[]>([]);
  protected readonly categories = computed(() =>
    [...new Set(this.materials().map(m => m.category).filter(Boolean) as string[])].sort());

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly trend = signal<TrendReport | null>(null);
  protected readonly anomalies = signal<AnomalyReport | null>(null);
  protected readonly sentiment = signal<SentimentReport | null>(null);
  protected readonly classification = signal<Classification | null>(null);
  private pollTimer?: ReturnType<typeof setInterval>;

  protected readonly canClassify = computed(() => {
    const c = this.classification();
    return !!c && c.aiAvailable && !c.job.running && c.byRules > 0 && this.auth.hasAnyRole('CONTROLLING');
  });

  protected readonly timelineChart = computed<EChartsOption | null>(() => {
    const s = this.sentiment();
    if (!s || !s.timeline.length) return null;
    const t = chartTheme();
    const labels = s.timeline.map(p => this.shortDate(p.periodStart));
    return {
      ...t.base,
      legend: { show: true, top: 0, textStyle: { color: t.label } },
      grid: { left: 40, right: 48, top: 36, bottom: 28 },
      tooltip: { trigger: 'axis' },
      xAxis: { ...t.categoryAxis, type: 'category', data: labels },
      yAxis: [
        { ...t.valueAxis, type: 'value', name: 'Bewertungen', nameTextStyle: { color: t.label } },
        { ...t.valueAxis, type: 'value', min: -1, max: 1, splitLine: { show: false }, axisLabel: { color: t.label } }
      ],
      series: [
        { type: 'bar', name: 'Positiv', stack: 's', data: s.timeline.map(p => p.positive), itemStyle: { color: t.palette[2] } },
        { type: 'bar', name: 'Neutral', stack: 's', data: s.timeline.map(p => p.neutral), itemStyle: { color: t.grid } },
        { type: 'bar', name: 'Negativ', stack: 's', data: s.timeline.map(p => p.negative), itemStyle: { color: t.palette[3] } },
        { type: 'line', name: 'Stimmungswert', yAxisIndex: 1, smooth: true, data: s.timeline.map(p => p.reviews ? p.avgScore : null),
          itemStyle: { color: t.palette[0] }, connectNulls: true }
      ]
    };
  });

  protected readonly emotionChart = computed<EChartsOption | null>(() => {
    const s = this.sentiment();
    if (!s || !s.emotions.length) return null;
    const t = chartTheme();
    return {
      ...t.base,
      legend: { show: true, type: 'scroll', bottom: 0, textStyle: { color: t.label } },
      tooltip: { trigger: 'item' },
      series: [{ type: 'pie', radius: ['45%', '70%'], center: ['50%', '45%'], label: { show: false },
        data: s.emotions.map(e => ({ name: e.text, value: e.count })), itemStyle: { borderRadius: 4 } }]
    };
  });

  ngOnInit(): void {
    this.setPreset(30, false);
    this.materialsApi.list().subscribe(m => this.materials.set(m.sort((a, b) => a.name.localeCompare(b.name))));
    this.loadClassification();
    this.analyze();
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  protected setMode(event: Event): void {
    const key = (event as CustomEvent<{ selectedItems: HTMLElement[] }>).detail.selectedItems[0]?.dataset['key'] as Mode;
    this.mode.set(key);
    if (key === 'anomalies' && this.daysInWindow() > 31) this.setPreset(14, false);
    this.analyze();
  }

  protected setPreset(days: number, reload = true): void {
    const start = new Date();
    start.setDate(start.getDate() - (days - 1));
    this.startDate = start.toISOString().slice(0, 10);
    this.endDate = todayIso();
    if (reload) this.analyze();
  }

  protected analyze(): void {
    const scope = this.scopeType === 'PRODUCT' && this.productId ? { productId: Number(this.productId) }
      : this.scopeType === 'CATEGORY' && this.category ? { category: this.category } : {};
    if (this.scopeType === 'PRODUCT' && !this.productId && this.mode() !== 'anomalies') {
      this.error.set('Bitte ein Material auswählen.');
      return;
    }
    if (this.scopeType === 'CATEGORY' && !this.category && this.mode() !== 'anomalies') {
      this.error.set('Bitte eine Warengruppe auswählen.');
      return;
    }
    this.error.set(null);
    switch (this.mode()) {
      case 'trend':
        this.run(this.api.trend(this.startDate, this.endDate, scope), r => this.trend.set(r));
        break;
      case 'anomalies':
        this.run(this.api.anomalies(this.startDate, this.endDate), r => this.anomalies.set(r));
        break;
      case 'sentiment':
        this.run(this.api.sentiment(this.startDate, this.endDate, scope), r => {
          this.sentiment.set(r);
          this.classification.set(r.classification);
        });
        break;
    }
  }

  protected startClassification(): void {
    this.api.startClassification().subscribe(c => {
      this.classification.set(c);
      this.startPolling();
    });
  }

  // ------------------------------------------------------------------
  // Anzeige
  // ------------------------------------------------------------------

  protected pct(share: number | null | undefined): number {
    return Math.round((share ?? 0) * 100);
  }

  protected directionText(d: ThemeDirection): string {
    return { IMPROVING: 'Verbessert', STABLE: 'Unverändert', DECLINING: 'Verschlechtert', NEW: 'Neu' }[d];
  }

  protected directionDesign(d: ThemeDirection): string {
    return { IMPROVING: 'Positive', STABLE: 'Neutral', DECLINING: 'Negative', NEW: 'Information' }[d];
  }

  protected severityText(s: string): string {
    return ({ HIGH: 'Hoch', MEDIUM: 'Mittel', LOW: 'Niedrig' } as Record<string, string>)[s] ?? s;
  }

  protected severityDesign(s: string): string {
    return ({ HIGH: 'Negative', MEDIUM: 'Critical', LOW: 'Information' } as Record<string, string>)[s] ?? 'Neutral';
  }

  protected sentimentDesign(s: string): string {
    return ({ POSITIVE: 'Positive', NEUTRAL: 'Neutral', NEGATIVE: 'Negative' } as Record<string, string>)[s] ?? 'Neutral';
  }

  protected sentimentText(s: string): string {
    return ({ POSITIVE: 'Positiv', NEUTRAL: 'Neutral', NEGATIVE: 'Negativ' } as Record<string, string>)[s] ?? s;
  }

  /** Veränderung mit Vorzeichen, z. B. "+0,4". */
  protected signed(value: number | null | undefined, digits = 1): string {
    if (value === null || value === undefined) return '–';
    const formatted = Math.abs(value).toLocaleString('de-DE', { minimumFractionDigits: digits, maximumFractionDigits: digits });
    return (value > 0 ? '+' : value < 0 ? '−' : '±') + formatted;
  }

  protected classificationText(c: Classification): string {
    if (c.job.running) return `KI ordnet die Bewertungen ein: ${c.job.processed} von ${c.job.total} …`;
    if (!c.aiAvailable) return `${c.reviews} Bewertungen regelbasiert eingeordnet. Ohne OpenAI-Schlüssel formuliert das System Zusammenfassungen aus den Kennzahlen.`;
    if (c.byRules === 0) return `Alle ${c.reviews} Bewertungen sind per KI eingeordnet (Stimmung, Emotion, Themen).`;
    return `${c.byAi} von ${c.reviews} Bewertungen per KI eingeordnet, ${c.byRules} regelbasiert.`;
  }

  private shortDate(iso: string): string {
    const [, m, d] = iso.split('-');
    return `${d}.${m}.`;
  }

  private daysInWindow(): number {
    return (new Date(this.endDate).getTime() - new Date(this.startDate).getTime()) / 86_400_000 + 1;
  }

  private loadClassification(): void {
    this.api.classification().subscribe({
      next: c => {
        this.classification.set(c);
        if (c.job.running) this.startPolling();
      },
      error: () => undefined
    });
  }

  private startPolling(): void {
    this.stopPolling();
    this.pollTimer = setInterval(() => this.api.classification().subscribe(c => {
      this.classification.set(c);
      if (!c.job.running) {
        this.stopPolling();
        this.analyze();
      }
    }), 5000);
  }

  private stopPolling(): void {
    if (this.pollTimer) clearInterval(this.pollTimer);
    this.pollTimer = undefined;
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
        this.error.set((e.error as { detail?: string })?.detail ?? 'Die Auswertung ist fehlgeschlagen.');
      }
    });
  }
}
