import { CUSTOM_ELEMENTS_SCHEMA, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { MessageService } from '../../core/message.service';
import { CustomersApi, MaterialsApi } from '../../core/master-data-api.service';
import { CreditExposure, Customer, Material, OrderCreateRequest, OrderSimulation } from '../../core/models';
import { OrdersApi } from '../../core/sales-api.service';
import { ProductAnomaly, ReviewInsightsService } from '../../core/review-insights.service';
import { StatusTagComponent } from '../../shared/status-tag.component';
import { todayIso } from '../../shared/status';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';

interface ItemDraft {
  key: number;
  productId: string;
  quantity: number;
  discountPercent: number;
}

/**
 * Auftragserfassung als Assistent (angelehnt an VA01): Auftraggeber → Positionen → Prüfen und Sichern.
 */
@Component({
  selector: 'om-order-create',
  imports: [FormsModule, RouterLink, Ui5ValueAccessorDirective, StatusTagComponent, CurrencyPipe, DecimalPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './order-create.component.html',
  styleUrl: './order-create.component.css'
})
export class OrderCreateComponent implements OnInit {
  private readonly ordersApi = inject(OrdersApi);
  private readonly customersApi = inject(CustomersApi);
  private readonly materialsApi = inject(MaterialsApi);
  private readonly messages = inject(MessageService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly customers = signal<Customer[]>([]);
  protected readonly materials = signal<Material[]>([]);
  protected readonly step = signal(1);
  protected readonly simulation = signal<OrderSimulation | null>(null);
  protected readonly exposure = signal<CreditExposure | null>(null);
  protected readonly busy = signal(false);

  protected readonly customerId = signal('');
  protected customerReference = '';
  protected requestedDeliveryDate = '';
  protected notes = '';
  protected items = signal<ItemDraft[]>([]);
  private nextKey = 1;

  protected readonly minDate = todayIso();

  protected readonly customer = computed(() => this.customers().find(c => String(c.id) === this.customerId()) ?? null);
  protected readonly validItems = computed(() => this.items().filter(i => i.productId && i.quantity > 0));

  /** Materialien mit aktueller Qualitätsauffälligkeit aus den Kundenbewertungen (Hinweis an der Position). */
  private readonly insightsApi = inject(ReviewInsightsService);
  protected readonly qualityAlerts = signal<ProductAnomaly[]>([]);

  protected qualityAlertOf(productId: string): ProductAnomaly | undefined {
    return productId ? this.qualityAlerts().find(a => String(a.productId) === productId) : undefined;
  }

  ngOnInit(): void {
    this.insightsApi.qualityAlerts().subscribe({ next: a => this.qualityAlerts.set(a), error: () => undefined });
    const date = new Date();
    date.setDate(date.getDate() + 3);
    this.requestedDeliveryDate = date.toISOString().slice(0, 10);
    this.addItem();

    forkJoin({ customers: this.customersApi.list(), materials: this.materialsApi.list() }).subscribe(({ customers, materials }) => {
      this.customers.set(customers.filter(c => c.active).sort((a, b) => a.lastName.localeCompare(b.lastName)));
      this.materials.set(materials.filter(m => m.active).sort((a, b) => a.materialNumber.localeCompare(b.materialNumber)));
      const preset = this.route.snapshot.queryParamMap.get('customerId');
      if (preset) this.selectCustomerId(preset);
    });
  }

  // ------------------------------------------------------------------
  // Schritt 1: Auftraggeber
  // ------------------------------------------------------------------

  protected customerSelected(event: Event): void {
    const item = (event as CustomEvent<{ item?: HTMLElement }>).detail.item;
    this.selectCustomerId(item?.dataset['id'] ?? '');
  }

  /**
   * Eingabe ohne Auswahl aus der Liste (Tippen + Enter oder Verlassen des Feldes):
   * eindeutige Debitorennummer oder ein eindeutiger Namensteil übernehmen.
   */
  protected customerTyped(event: Event): void {
    const value = ((event.target as HTMLInputElement).value ?? '').trim().toLowerCase();
    const current = this.customer();
    if (!value || (current && this.customerLabel(current).toLowerCase() === value)) return;
    const exact = this.customers().find(c => c.customerNumber === value);
    const matches = exact ? [exact] : this.customers().filter(c => this.customerLabel(c).toLowerCase().includes(value));
    if (matches.length === 1) this.selectCustomerId(String(matches[0].id));
  }

  private selectCustomerId(id: string): void {
    this.customerId.set(id);
    this.exposure.set(null);
    this.simulation.set(null);
    if (id) this.customersApi.creditExposure(Number(id)).subscribe(e => this.exposure.set(e));
  }

  protected customerLabel(c: Customer): string {
    return `${c.fullName} (${c.customerNumber})`;
  }

  // ------------------------------------------------------------------
  // Schritt 2: Positionen
  // ------------------------------------------------------------------

  protected addItem(): void {
    this.items.update(list => [...list, { key: this.nextKey++, productId: '', quantity: 1, discountPercent: 0 }]);
  }

  protected removeItem(key: number): void {
    this.items.update(list => list.filter(i => i.key !== key));
    this.simulation.set(null);
  }

  protected updateItem(key: number, changes: Partial<ItemDraft>): void {
    this.items.update(list => list.map(i => (i.key === key ? { ...i, ...changes } : i)));
    this.simulation.set(null);
  }

  protected materialSelected(key: number, event: Event): void {
    const item = (event as CustomEvent<{ item?: HTMLElement }>).detail.item;
    this.updateItem(key, { productId: item?.dataset['id'] ?? '' });
  }

  /** Wie beim Auftraggeber: eindeutige Materialnummer oder eindeutiger Namensteil. */
  protected materialTyped(key: number, event: Event): void {
    const value = ((event.target as HTMLInputElement).value ?? '').trim().toLowerCase();
    const item = this.items().find(i => i.key === key);
    const current = item ? this.materialOf(item.productId) : undefined;
    if (!value || (current && this.materialLabel(current).toLowerCase() === value)) return;
    const exact = this.materials().find(m => m.materialNumber === value);
    const matches = exact ? [exact] : this.materials().filter(m => this.materialLabel(m).toLowerCase().includes(value));
    if (matches.length === 1) this.updateItem(key, { productId: String(matches[0].id) });
  }

  protected materialOf(productId: string): Material | undefined {
    return this.materials().find(m => String(m.id) === productId);
  }

  protected materialLabel(m: Material): string {
    return `${m.materialNumber} · ${m.name}`;
  }

  /** Simulationsergebnis zur Position, sofern die Reihenfolge noch übereinstimmt. */
  protected simulated(index: number) {
    return this.simulation()?.items[index] ?? null;
  }

  // ------------------------------------------------------------------
  // Navigation und Sichern
  // ------------------------------------------------------------------

  protected goTo(step: number): void {
    if (step === 2 && !this.customer()) return;
    if (step === 3) {
      if (!this.validItems().length) return;
      this.simulate();
    }
    this.step.set(step);
  }

  protected stepChange(event: Event): void {
    const stepEl = (event as CustomEvent<{ step: HTMLElement }>).detail.step;
    const target = Number(stepEl.dataset['step']);
    if (target) this.goTo(target);
  }

  protected simulate(): void {
    if (!this.customer() || !this.validItems().length) return;
    this.busy.set(true);
    this.ordersApi.simulate(this.request()).subscribe({
      next: result => {
        this.simulation.set(result);
        this.busy.set(false);
      },
      error: () => this.busy.set(false)
    });
  }

  protected save(): void {
    this.busy.set(true);
    this.ordersApi.create(this.request()).subscribe({
      next: order => {
        this.busy.set(false);
        this.messages.success(`Kundenauftrag ${order.orderNumber} wurde gesichert`
          + (order.creditStatus.code === 'BLOCKED' ? ' (Kreditsperre)' : ''));
        this.router.navigate(['/orders', order.id]);
      },
      error: () => this.busy.set(false)
    });
  }

  protected cancel(): void {
    this.router.navigate(['/orders']);
  }

  private request(): OrderCreateRequest {
    return {
      customerId: Number(this.customerId()),
      requestedDeliveryDate: this.requestedDeliveryDate || null,
      customerReference: this.customerReference || null,
      notes: this.notes || null,
      items: this.validItems().map(i => ({
        productId: Number(i.productId),
        quantity: Number(i.quantity),
        discountPercent: Number(i.discountPercent) || null
      }))
    };
  }
}
