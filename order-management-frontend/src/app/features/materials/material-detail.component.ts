import { CUSTOM_ELEMENTS_SCHEMA, Component, computed, effect, inject, input, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { MessageService } from '../../core/message.service';
import { InventoryApi, MaterialsApi } from '../../core/master-data-api.service';
import { Availability, ChangeDocument, Material, StockMovement, WarehouseStock } from '../../core/models';
import { STOCK_STATUS_TEXT, semanticOf } from '../../shared/status';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';
import { MovementDialogComponent } from '../inventory/movement-dialog.component';
import { ProductAnomaly, ReviewInsightsService, ThemeDirection, TrendReport } from '../../core/review-insights.service';
import { Review, ReviewsService } from '../../core/reviews.service';
import { ReviewReplyDialogComponent } from '../../shared/review-reply-dialog.component';
import { todayIso } from '../../shared/status';
import { MaterialDialogComponent } from './material-dialog.component';

@Component({
  selector: 'om-material-detail',
  imports: [FormsModule, RouterLink, Ui5ValueAccessorDirective, MaterialDialogComponent, MovementDialogComponent, ReviewReplyDialogComponent,
    CurrencyPipe, DatePipe, DecimalPipe, PercentPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './material-detail.component.html',
  styles: `
    .hero { width: 7rem; height: 7rem; border-radius: 1rem; object-fit: cover; background: var(--sapNeutralBackgroundColor); }
    .header-row { display: flex; gap: 2rem; align-items: center; flex-wrap: wrap; }
    .stock-grid { display: grid; gap: 1rem; grid-template-columns: minmax(0, 3fr) minmax(20rem, 2fr); }
    @media (max-width: 1100px) { .stock-grid { grid-template-columns: minmax(0, 1fr); } }
    .atp { display: grid; grid-template-columns: minmax(0, 1fr) max-content; gap: .25rem 1.5rem; max-width: 28rem; }
    .atp .total { border-top: 1px solid var(--sapGroup_ContentBorderColor); padding-top: .25rem; }
    .quality { margin-top: .75rem; }
    .voice-grid { display: grid; gap: 1rem; grid-template-columns: minmax(0, 2fr) minmax(18rem, 1fr); align-items: start; }
    @media (max-width: 1100px) { .voice-grid { grid-template-columns: minmax(0, 1fr); } }
    .summary { line-height: 1.5; margin: 0; }
    .reviews { display: flex; flex-direction: column; gap: .75rem; }
    .review { padding: .75rem 1rem; border-radius: .75rem; border: 1px solid var(--sapGroup_ContentBorderColor);
      background: var(--sapGroup_ContentBackground); display: flex; flex-direction: column; gap: .4rem; }
    .review p { margin: 0; line-height: 1.45; }
    .review .meta { display: flex; justify-content: space-between; align-items: center; gap: .5rem; flex-wrap: wrap; }
    .reply { margin-left: 1rem; padding: .5rem .75rem; border-left: 3px solid var(--sapBrandColor);
      background: var(--sapList_Background); white-space: pre-line; }
    .tags { display: flex; gap: .25rem; flex-wrap: wrap; }
    .themes li { margin-bottom: .35rem; }
  `
})
export class MaterialDetailComponent {
  readonly id = input.required<string>();

  protected readonly auth = inject(AuthService);
  private readonly materialsApi = inject(MaterialsApi);
  private readonly inventoryApi = inject(InventoryApi);
  private readonly messages = inject(MessageService);

  protected readonly material = signal<Material | null>(null);
  protected readonly availability = signal<Availability | null>(null);
  protected readonly stocks = signal<WarehouseStock[]>([]);
  protected readonly movements = signal<StockMovement[]>([]);
  protected readonly changes = signal<ChangeDocument[]>([]);
  protected readonly dialog = signal<'edit' | 'movement' | 'settings' | null>(null);
  protected readonly tab = signal<'stock' | 'movements' | 'master' | 'voice' | 'changes'>('stock');

  // Kundenstimmen
  private readonly reviewsApi = inject(ReviewsService);
  private readonly insightsApi = inject(ReviewInsightsService);
  protected readonly qualityAlert = signal<ProductAnomaly | null>(null);
  protected readonly voice = signal<TrendReport | null>(null);
  protected readonly reviews = signal<Review[]>([]);
  protected readonly visibleReviews = signal(15);
  protected readonly voiceLoading = signal(false);
  protected readonly replyTarget = signal<Review | null>(null);
  protected readonly openCritical = computed(() => this.reviews().filter(r => r.rating <= 2 && !r.replyText).length);
  protected readonly busy = signal(false);
  protected readonly stockStatusText = STOCK_STATUS_TEXT;
  protected readonly semanticOf = semanticOf;

  protected settings = { warehouseId: 0, warehouseName: '', minStock: 0, maxStock: 0, binLocation: '' };

  protected readonly margin = computed(() => {
    const m = this.material();
    return m?.costPrice && m.price ? (m.price - m.costPrice) / m.price : null;
  });

  protected readonly stockValue = computed(() => {
    const m = this.material();
    return m ? (m.costPrice ?? m.price) * m.stockQuantity : 0;
  });

  constructor() {
    effect(() => {
      const id = Number(this.id());
      if (id) this.load(id);
    });
  }

  protected load(id = Number(this.id())): void {
    forkJoin({
      material: this.materialsApi.get(id),
      availability: this.inventoryApi.availability(id),
      stocks: this.inventoryApi.stockByProduct(id),
      movements: this.inventoryApi.movementsByProduct(id),
      changes: this.materialsApi.changes(id)
    }).subscribe(r => {
      this.material.set(r.material);
      this.availability.set(r.availability);
      this.stocks.set(r.stocks.sort((a, b) => a.warehouseCode.localeCompare(b.warehouseCode)));
      this.movements.set(r.movements);
      this.changes.set(r.changes);
    });
    this.insightsApi.qualityAlerts().subscribe({
      next: alerts => this.qualityAlert.set(alerts.find(a => a.productId === id) ?? null),
      error: () => this.qualityAlert.set(null)
    });
    this.voice.set(null);
    if (this.tab() === 'voice') this.loadVoice(id);
  }

  protected onTabSelect(event: Event): void {
    const key = (event as CustomEvent<{ tab: HTMLElement }>).detail.tab.dataset['key'] as ReturnType<typeof this.tab>;
    this.tab.set(key);
    if (key === 'voice' && !this.voice()) this.loadVoice(Number(this.id()));
  }

  protected showVoice(): void {
    this.tab.set('voice');
    if (!this.voice()) this.loadVoice(Number(this.id()));
  }

  /** Kundenstimmen der letzten 90 Tage: Kennzahlen und Einordnung im Vergleich zum Vorzeitraum, dazu alle Bewertungen. */
  private loadVoice(id: number): void {
    const start = new Date();
    start.setDate(start.getDate() - 89);
    this.voiceLoading.set(true);
    forkJoin({
      trend: this.insightsApi.trend(start.toISOString().slice(0, 10), todayIso(), { productId: id }),
      reviews: this.reviewsApi.forProduct(id)
    }).subscribe({
      next: r => {
        this.voice.set(r.trend);
        this.reviews.set(r.reviews);
        this.voiceLoading.set(false);
      },
      error: () => this.voiceLoading.set(false)
    });
  }

  protected replySaved(saved: Review): void {
    this.reviews.update(list => list.map(r => (r.id === saved.id ? saved : r)));
    this.replyTarget.set(null);
  }

  protected directionText(d: ThemeDirection): string {
    return { IMPROVING: 'verbessert', STABLE: 'unverändert', DECLINING: 'verschlechtert', NEW: 'neu' }[d];
  }

  protected sentimentDesign(s: string | null): string {
    return ({ POSITIVE: 'Positive', NEUTRAL: 'Neutral', NEGATIVE: 'Negative' } as Record<string, string>)[s ?? ''] ?? 'Neutral';
  }

  protected sentimentText(s: string | null): string {
    return ({ POSITIVE: 'Positiv', NEUTRAL: 'Neutral', NEGATIVE: 'Negativ' } as Record<string, string>)[s ?? ''] ?? '';
  }

  protected pct(share: number): number {
    return Math.round(share * 100);
  }

  protected editSettings(s: WarehouseStock): void {
    this.settings = { warehouseId: s.warehouseId, warehouseName: `${s.warehouseCode} ${s.warehouseName}`,
      minStock: s.minStock, maxStock: s.maxStock, binLocation: s.binLocation ?? '' };
    this.dialog.set('settings');
  }

  protected saveSettings(): void {
    this.busy.set(true);
    this.inventoryApi.updateStockSettings(this.settings.warehouseId, this.material()!.id, {
      minStock: Number(this.settings.minStock), maxStock: Number(this.settings.maxStock), binLocation: this.settings.binLocation
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.dialog.set(null);
        this.messages.success('Dispositionsdaten gesichert');
        this.load();
      },
      error: () => this.busy.set(false)
    });
  }

  protected block(): void {
    this.materialsApi.block(this.material()!.id).subscribe(() => {
      this.messages.success('Vertriebssperre gesetzt');
      this.load();
    });
  }

  protected afterSave(): void {
    this.dialog.set(null);
    this.load();
  }

  protected changeText(c: ChangeDocument): string {
    return c.description ?? `${c.fieldName}: ${c.oldValue ?? '–'} → ${c.newValue ?? '–'}`;
  }
}
