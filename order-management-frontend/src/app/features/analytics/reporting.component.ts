import { CUSTOM_ELEMENTS_SCHEMA, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { NgxEchartsDirective } from 'ngx-echarts';
import type { EChartsOption } from 'echarts';
import { ReportingApi } from '../../core/analytics-api.service';
import { AbcAnalysis, KpiDashboard, SalesForecast } from '../../core/models';
import { chartTheme } from '../../shared/charts';

type AbcScope = 'products' | 'customers';

interface ForecastRow {
  period: string;
  actual: number | null;
  forecast: number | null;
  orders: number;
  growth: number | null;
  running: boolean;
}

/** Analysen: Kennzahlen (wie Fiori Overview Page), ABC-Analyse und Umsatzprognose. Basis sind die Fakturen. */
@Component({
  selector: 'om-reporting',
  imports: [NgxEchartsDirective, CurrencyPipe, DecimalPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './reporting.component.html',
  styles: `
    .kpis { display: grid; grid-template-columns: repeat(auto-fill, minmax(12rem, 1fr)); gap: .75rem; }
    .kpi-card { padding: 1rem; border-radius: .75rem; border: 1px solid var(--sapGroup_ContentBorderColor);
      background: var(--sapGroup_ContentBackground); display: flex; flex-direction: column; gap: .25rem; }
    .kpi-card .label { color: var(--sapContent_LabelColor); font-size: var(--sapFontSmallSize); }
    .kpi-card .value { font-size: 1.75rem; font-family: var(--sapFontLightFamily, var(--sapFontFamily)); color: var(--sapTextColor); }
    .kpi-card .value[data-state='Positive'] { color: var(--sapPositiveTextColor); }
    .kpi-card .value[data-state='Negative'] { color: var(--sapNegativeTextColor); }
    .kpi-card .value[data-state='Critical'] { color: var(--sapCriticalTextColor); }
    .kpi-card .sub { color: var(--sapContent_LabelColor); font-size: var(--sapFontSmallSize); }
    .group-title { margin: 1rem 0 .5rem; }
    .abc-classes { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: .75rem; }
    ui5-tabcontainer { --_ui5_tc_content_padding: 0; }
  `
})
export class ReportingComponent implements OnInit {
  private readonly api = inject(ReportingApi);
  private readonly router = inject(Router);

  protected readonly tab = signal<'kpi' | 'abc' | 'forecast'>('kpi');
  protected readonly kpis = signal<KpiDashboard | null>(null);
  protected readonly abc = signal<AbcAnalysis | null>(null);
  protected readonly abcScope = signal<AbcScope>('products');
  protected readonly forecast = signal<SalesForecast | null>(null);
  protected readonly loading = signal(true);
  protected readonly abcLoading = signal(false);

  protected readonly revenueChart = computed<EChartsOption | null>(() => {
    const k = this.kpis();
    if (!k) return null;
    const t = chartTheme();
    return {
      ...t.base,
      grid: { left: 64, right: 16, top: 16, bottom: 32 },
      tooltip: { trigger: 'axis', valueFormatter: v => t.currency(Number(v)) },
      xAxis: { ...t.categoryAxis, type: 'category', data: k.revenueTrend.map(p => p.period) },
      yAxis: { ...t.valueAxis, type: 'value' },
      series: [{ type: 'bar', name: 'Nettoumsatz', data: k.revenueTrend.map(p => p.value), itemStyle: { color: t.palette[0], borderRadius: [4, 4, 0, 0] } }]
    };
  });

  protected readonly orderChart = computed<EChartsOption | null>(() => {
    const k = this.kpis();
    if (!k) return null;
    const t = chartTheme();
    return {
      ...t.base,
      grid: { left: 48, right: 16, top: 16, bottom: 32 },
      tooltip: { trigger: 'axis' },
      xAxis: { ...t.categoryAxis, type: 'category', data: k.orderTrend.map(p => p.period) },
      yAxis: { ...t.valueAxis, type: 'value' },
      series: [{ type: 'line', name: 'Aufträge', smooth: true, data: k.orderTrend.map(p => p.value),
        itemStyle: { color: t.palette[2] }, areaStyle: { opacity: .12 } }]
    };
  });

  protected readonly inventoryChart = computed<EChartsOption | null>(() => {
    const k = this.kpis();
    if (!k) return null;
    const t = chartTheme();
    const data = Object.entries(k.inventory.inventoryByCategory ?? {}).map(([name, value]) => ({ name, value }));
    return {
      ...t.base,
      tooltip: { trigger: 'item', valueFormatter: v => t.currency(Number(v)) },
      legend: { type: 'scroll', bottom: 0, textStyle: { color: t.label } },
      series: [{ type: 'pie', radius: ['45%', '70%'], center: ['50%', '45%'], data, label: { show: false }, itemStyle: { borderRadius: 4 } }]
    };
  });

  protected readonly countryChart = computed<EChartsOption | null>(() => {
    const k = this.kpis();
    if (!k) return null;
    const t = chartTheme();
    const entries = Object.entries(k.customer.customersByCountry ?? {}).sort((a, b) => a[1] - b[1]);
    return {
      ...t.base,
      grid: { left: 96, right: 16, top: 8, bottom: 24 },
      tooltip: { trigger: 'axis' },
      xAxis: { ...t.valueAxis, type: 'value' },
      yAxis: { ...t.categoryAxis, type: 'category', data: entries.map(e => e[0]) },
      series: [{ type: 'bar', name: 'Debitoren', data: entries.map(e => e[1]), itemStyle: { color: t.palette[4], borderRadius: [0, 4, 4, 0] } }]
    };
  });

  /** Pareto-Diagramm: Umsatz je Objekt (Balken) und kumulierter Anteil (Linie). */
  protected readonly paretoChart = computed<EChartsOption | null>(() => {
    const a = this.abc();
    if (!a) return null;
    const t = chartTheme();
    const items = a.items.slice(0, 40);
    const colors: Record<string, string> = { A: t.palette[0], B: t.palette[1], C: t.palette[3] };
    return {
      ...t.base,
      grid: { left: 64, right: 48, top: 24, bottom: 32 },
      tooltip: { trigger: 'axis' },
      xAxis: { ...t.categoryAxis, type: 'category', data: items.map(i => i.number), axisLabel: { show: false } },
      yAxis: [
        { ...t.valueAxis, type: 'value' },
        { ...t.valueAxis, type: 'value', max: 100, axisLabel: { color: t.label, formatter: '{value} %' }, splitLine: { show: false } }
      ],
      series: [
        {
          type: 'bar', name: 'Umsatz',
          data: items.map(i => ({ value: i.revenue, itemStyle: { color: colors[i.abcClass] } })),
          tooltip: { valueFormatter: v => t.currency(Number(v)) }
        },
        {
          type: 'line', name: 'Kumuliert', yAxisIndex: 1, smooth: true, symbol: 'none',
          data: items.map(i => i.cumulativePercentage), itemStyle: { color: t.text },
          tooltip: { valueFormatter: v => `${t.number(Number(v))} %` },
          markLine: { silent: true, symbol: 'none', lineStyle: { type: 'dashed', color: t.label },
            data: [{ yAxis: 80 }, { yAxis: 95 }], label: { position: 'insideStartTop', formatter: 'Grenze {c} %', color: t.label } }
        }
      ]
    };
  });

  /**
   * Ist- und Prognosemonate zusammengeführt. Der laufende Monat kommt in beiden Listen vor:
   * Ist bisher (unvollständig) und Prognose für den ganzen Monat.
   */
  protected readonly periods = computed<ForecastRow[]>(() => {
    const f = this.forecast();
    if (!f) return [];
    const rows = new Map<string, ForecastRow>();
    const current = f.forecastData[0]?.period;
    for (const p of f.historicalData) {
      rows.set(p.period, { period: p.period, actual: p.actualRevenue ?? null, forecast: null, orders: p.orderCount,
        growth: p.period === current ? null : p.growthRate ?? null, running: p.period === current });
    }
    for (const p of f.forecastData) {
      const row = rows.get(p.period);
      if (row) row.forecast = p.forecastedRevenue ?? null;
      else rows.set(p.period, { period: p.period, actual: null, forecast: p.forecastedRevenue ?? null, orders: 0, growth: null, running: false });
    }
    return [...rows.values()];
  });

  protected readonly forecastChart = computed<EChartsOption | null>(() => {
    const rows = this.periods();
    if (!rows.length) return null;
    const t = chartTheme();
    // Die Prognoselinie beginnt am letzten abgeschlossenen Monat, damit sie an die Ist-Werte anschließt
    const firstForecast = rows.findIndex(r => r.forecast !== null);
    return {
      ...t.base,
      grid: { left: 64, right: 16, top: 32, bottom: 32 },
      legend: { top: 0, textStyle: { color: t.label } },
      tooltip: { trigger: 'axis', valueFormatter: v => (v === null || v === undefined || v === '-' ? '–' : t.currency(Number(v))) },
      xAxis: { ...t.categoryAxis, type: 'category', data: rows.map(r => (r.running ? `${r.period} (lfd.)` : r.period)) },
      yAxis: { ...t.valueAxis, type: 'value' },
      series: [
        { type: 'bar', name: 'Ist-Umsatz', data: rows.map(r => r.actual),
          itemStyle: { color: t.palette[0], borderRadius: [4, 4, 0, 0] } },
        { type: 'line', name: 'Prognose', data: rows.map((r, i) => (i === firstForecast - 1 ? r.actual : r.forecast)),
          lineStyle: { type: 'dashed', width: 2 }, itemStyle: { color: t.palette[1] } }
      ]
    };
  });

  ngOnInit(): void {
    this.api.kpis().subscribe({
      next: k => {
        this.kpis.set(k);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
    this.loadAbc();
    this.api.forecast(12, 6).subscribe(f => this.forecast.set(f));
  }

  /** Reiterinhalte werden erst beim Anzeigen gerendert, damit die Diagramme ihre Größe kennen. */
  protected selectTab(event: Event): void {
    const tab = (event as CustomEvent<{ tab: HTMLElement }>).detail.tab;
    this.tab.set(tab.dataset['key'] as 'kpi' | 'abc' | 'forecast');
  }

  protected setAbcScope(event: Event): void {
    const key = (event as CustomEvent<{ selectedItems: HTMLElement[] }>).detail.selectedItems[0]?.dataset['key'] as AbcScope;
    this.abcScope.set(key);
    this.loadAbc();
  }

  protected openAbcItem(event: Event): void {
    const id = (event as CustomEvent<{ row: { rowKey: string } }>).detail.row.rowKey;
    this.router.navigate([this.abcScope() === 'products' ? '/materials' : '/customers', id]);
  }

  protected classDesign(c: string): string {
    return { A: 'Positive', B: 'Information', C: 'Neutral' }[c] ?? 'Neutral';
  }

  protected trendText(direction: string): string {
    return { UP: 'Steigend', DOWN: 'Fallend', STABLE: 'Stabil' }[direction] ?? direction;
  }

  private loadAbc(): void {
    this.abcLoading.set(true);
    const request = this.abcScope() === 'products' ? this.api.productAbc() : this.api.customerAbc();
    request.subscribe({
      next: a => {
        this.abc.set(a);
        this.abcLoading.set(false);
      },
      error: () => this.abcLoading.set(false)
    });
  }
}
