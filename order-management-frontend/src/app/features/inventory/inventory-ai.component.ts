import { CUSTOM_ELEMENTS_SCHEMA, Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { HttpClient, HttpContext } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { API_URL } from '../../core/api';
import { SILENT_ERRORS } from '../../core/http.interceptors';

interface DemandForecast {
  productId: number;
  productName: string;
  category: string;
  currentStock: number;
  dailyDemand: number;
  predictedDemand7Days: number;
  predictedDemand14Days: number;
  predictedDemand30Days: number;
  trendDirection: 'RISING' | 'STABLE' | 'FALLING';
  confidence: number;
  reachDays: number | null;
  stockoutRiskDate: string | null;
  backlog: number;
}

interface ReorderRecommendation {
  productId: number;
  productName: string;
  warehouseName: string;
  currentStock: number;
  minStock: number;
  maxStock: number;
  dailyDemand: number;
  backlog: number;
  recommendedOrderQuantity: number;
  urgency: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  reason: string;
  estimatedCost: number;
  suggestedOrderDate: string;
  daysUntilStockout: number | null;
}

interface InventoryAnomaly {
  productId: number;
  productName: string;
  warehouseName: string;
  anomalyType: 'QUALITY_RISK' | 'STOCKOUT_WITH_BACKLOG' | 'DEMAND_SPIKE' | 'OVERSTOCK' | 'SLOW_MOVER';
  severity: 'HIGH' | 'MEDIUM' | 'LOW';
  description: string;
  suggestedAction: string;
}

interface HealthScore {
  overallScore: number;
  grade: string;
  stockAvailabilityScore: number;
  turnoverScore: number;
  accuracyScore: number;
  turnoverRate: number;
  strengths: string[];
  weaknesses: string[];
}

interface InventoryReport {
  generatedAt: string;
  executiveSummary: string;
  actionItems: string[];
  narrativeSource: 'KI' | 'REGELN';
  method: string;
  demandForecasts: DemandForecast[];
  reorderRecommendations: ReorderRecommendation[];
  anomalies: InventoryAnomaly[];
  healthScore: HealthScore;
}

/**
 * Bestandsanalyse: Bedarfsprognose, Nachschubvorschläge und Auffälligkeiten. Die Zahlen sind berechnet,
 * die Zusammenfassung formuliert bei verfügbarer KI das Sprachmodell.
 */
@Component({
  selector: 'om-inventory-ai',
  imports: [RouterLink, CurrencyPipe, DatePipe, DecimalPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './inventory-ai.component.html',
  styles: `
    .scores { display: grid; grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr)); gap: 1rem; }
    .score { display: flex; flex-direction: column; gap: .25rem; }
    .grade { font-size: 3rem; line-height: 1; font-family: var(--sapFontLightFamily, var(--sapFontFamily)); color: var(--sapBrandColor); }
    ul { margin: .25rem 0 0; padding-left: 1.25rem; }
    li { margin-bottom: .25rem; }
    .summary { line-height: 1.5; margin: 0; }
  `
})
export class InventoryAiComponent implements OnInit {
  private readonly http = inject(HttpClient);

  protected readonly report = signal<InventoryReport | null>(null);
  protected readonly loading = signal(false);
  protected readonly failed = signal(false);
  protected readonly visibleForecasts = signal(20);

  protected showMore(): void {
    this.visibleForecasts.update(n => n + 20);
  }

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.failed.set(false);
    // Fehler werden hier als eigene Hinweisseite angezeigt statt als Meldungsdialog
    this.http.get<InventoryReport>(`${API_URL}/inventory/ai/report`, { context: new HttpContext().set(SILENT_ERRORS, true) })
      .subscribe({
        next: r => {
          this.report.set(r);
          this.loading.set(false);
        },
        error: () => {
          this.failed.set(true);
          this.loading.set(false);
        }
      });
  }

  protected urgencyDesign(u: string): string {
    return { CRITICAL: 'Negative', HIGH: 'Critical', MEDIUM: 'Information', LOW: 'Neutral' }[u] ?? 'Neutral';
  }

  protected urgencyText(u: string): string {
    return { CRITICAL: 'Kritisch', HIGH: 'Hoch', MEDIUM: 'Mittel', LOW: 'Niedrig' }[u] ?? u;
  }

  protected trendText(t: string): string {
    return { RISING: 'Steigend', STABLE: 'Stabil', FALLING: 'Fallend' }[t] ?? t;
  }

  protected anomalyText(t: string): string {
    return {
      QUALITY_RISK: 'Qualitätsrisiko', STOCKOUT_WITH_BACKLOG: 'Ausverkauft mit Rückstand', DEMAND_SPIKE: 'Nachfragespitze',
      OVERSTOCK: 'Überbestand', SLOW_MOVER: 'Ladenhüter'
    }[t] ?? t;
  }

  protected severityDesign(s: string): string {
    return { HIGH: 'Negative', MEDIUM: 'Critical', LOW: 'Information' }[s] ?? 'Neutral';
  }

  protected confidenceText(c: number): string {
    return c >= 0.7 ? 'hoch' : c >= 0.45 ? 'mittel' : 'niedrig';
  }

  protected scoreState(score: number): string {
    return score >= 80 ? 'Positive' : score >= 60 ? 'Critical' : 'Negative';
  }
}
