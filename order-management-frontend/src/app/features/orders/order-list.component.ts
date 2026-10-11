import { CUSTOM_ELEMENTS_SCHEMA, Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { MessageService } from '../../core/message.service';
import { OrderListItem } from '../../core/models';
import { OrderSearch, OrdersApi } from '../../core/sales-api.service';
import { StatusTagComponent } from '../../shared/status-tag.component';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';

interface Variant {
  key: string;
  text: string;
  status: string[];
  creditStatus: string;
}

const PAGE_SIZE = 25;

@Component({
  selector: 'om-order-list',
  imports: [FormsModule, Ui5ValueAccessorDirective, StatusTagComponent, CurrencyPipe, DatePipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './order-list.component.html',
  styles: `
    .variant { display: flex; align-items: center; gap: .5rem; }
    .variant-select { min-width: 14rem; }
    .customer-filter { margin-top: .75rem; }
  `
})
export class OrderListComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(OrdersApi);
  private readonly messages = inject(MessageService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly variants: Variant[] = [
    { key: 'standard', text: 'Standard', status: [], creditStatus: '' },
    { key: 'open', text: 'Offene Aufträge', status: ['OPEN', 'IN_PROCESS'], creditStatus: '' },
    { key: 'credit', text: 'Kreditsperren', status: ['OPEN', 'IN_PROCESS'], creditStatus: 'BLOCKED' },
    { key: 'completed', text: 'Erledigte Aufträge', status: ['COMPLETED'], creditStatus: '' },
    { key: 'cancelled', text: 'Stornierte Aufträge', status: ['CANCELLED'], creditStatus: '' }
  ];

  protected readonly statusOptions = [
    { code: 'OPEN', text: 'Offen' },
    { code: 'IN_PROCESS', text: 'In Bearbeitung' },
    { code: 'COMPLETED', text: 'Erledigt' },
    { code: 'CANCELLED', text: 'Storniert' }
  ];

  protected readonly processingOptions = [
    { code: '', text: 'Alle' },
    { code: 'NOT_PROCESSED', text: 'Nicht bearbeitet' },
    { code: 'PARTIALLY_PROCESSED', text: 'Teilweise bearbeitet' },
    { code: 'FULLY_PROCESSED', text: 'Vollständig bearbeitet' }
  ];

  protected readonly creditOptions = [
    { code: '', text: 'Alle' },
    { code: 'APPROVED', text: 'Freigegeben' },
    { code: 'BLOCKED', text: 'Kreditsperre' },
    { code: 'RELEASED', text: 'Manuell freigegeben' }
  ];

  protected readonly sortOptions = [
    { code: 'orderDate,desc', text: 'Belegdatum (neueste zuerst)' },
    { code: 'orderDate,asc', text: 'Belegdatum (älteste zuerst)' },
    { code: 'netAmount,desc', text: 'Nettowert (höchster zuerst)' },
    { code: 'requestedDeliveryDate,asc', text: 'Wunschlieferdatum' },
    { code: 'orderNumber,desc', text: 'Auftragsnummer' }
  ];

  protected variant = 'standard';
  protected search = '';
  protected statuses: string[] = [];
  protected creditStatus = '';
  protected deliveryStatus = '';
  protected billingStatus = '';
  protected dateFrom = '';
  protected dateTo = '';
  protected sort = 'orderDate,desc';
  protected customerId: number | null = null;

  protected readonly rows = signal<OrderListItem[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  private page = 0;

  ngOnInit(): void {
    const q = this.route.snapshot.queryParamMap;
    this.search = q.get('search') ?? '';
    this.statuses = (q.get('status') ?? '').split(',').filter(Boolean);
    this.creditStatus = q.get('creditStatus') ?? '';
    this.deliveryStatus = q.get('deliveryStatus') ?? '';
    this.billingStatus = q.get('billingStatus') ?? '';
    this.customerId = q.get('customerId') ? Number(q.get('customerId')) : null;
    this.variant = this.variants.find(v => this.matches(v))?.key ?? 'standard';
    this.go();
  }

  protected applyVariant(event: Event): void {
    const key = (event as CustomEvent<{ selectedOption: { value: string } }>).detail.selectedOption.value;
    const v = this.variants.find(x => x.key === key)!;
    this.variant = key;
    this.statuses = [...v.status];
    this.creditStatus = v.creditStatus;
    this.deliveryStatus = '';
    this.billingStatus = '';
    this.go();
  }

  protected statusSelection(event: Event): void {
    const items = (event as CustomEvent<{ items: HTMLElement[] }>).detail.items;
    this.statuses = items.map(i => i.dataset['code']!).filter(Boolean);
  }

  protected reset(): void {
    this.search = '';
    this.statuses = [];
    this.creditStatus = '';
    this.deliveryStatus = '';
    this.billingStatus = '';
    this.dateFrom = '';
    this.dateTo = '';
    this.customerId = null;
    this.variant = 'standard';
    this.go();
  }

  protected go(): void {
    this.page = 0;
    this.rows.set([]);
    this.updateUrl();
    this.load();
  }

  protected more(): void {
    if (this.rows().length >= this.total()) return;
    this.page++;
    this.load();
  }

  protected open(event: Event): void {
    const key = (event as CustomEvent<{ row: { rowKey: string } }>).detail.row.rowKey;
    this.router.navigate(['/orders', key]);
  }

  protected create(): void {
    this.router.navigate(['/orders/new']);
  }

  protected processBacklog(): void {
    this.api.processBacklog().subscribe(result => {
      this.messages.success(result.improvedItems > 0
        ? `Rückstandsbearbeitung: ${result.improvedItems} Positionen neu bestätigt`
        : 'Rückstandsbearbeitung: keine zusätzliche Menge verfügbar');
      this.go();
    });
  }

  protected isLate(o: OrderListItem): boolean {
    if (!o.requestedDeliveryDate || o.status.code === 'COMPLETED' || o.status.code === 'CANCELLED') return false;
    return o.deliveryStatus.code !== 'FULLY_PROCESSED' && new Date(o.requestedDeliveryDate) < new Date(new Date().toDateString());
  }

  private load(): void {
    this.loading.set(true);
    const criteria: OrderSearch = {
      search: this.search,
      status: this.statuses,
      creditStatus: this.creditStatus,
      deliveryStatus: this.deliveryStatus,
      billingStatus: this.billingStatus,
      dateFrom: this.dateFrom,
      dateTo: this.dateTo,
      customerId: this.customerId ?? undefined,
      page: this.page,
      size: PAGE_SIZE,
      sort: this.sort
    };
    this.api.search(criteria).subscribe({
      next: page => {
        this.rows.update(rows => [...rows, ...page.content]);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  private matches(v: Variant): boolean {
    return [...v.status].sort().join(',') === [...this.statuses].sort().join(',')
      && v.creditStatus === this.creditStatus && !this.deliveryStatus && !this.billingStatus;
  }

  private updateUrl(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      replaceUrl: true,
      queryParams: {
        search: this.search || null,
        status: this.statuses.length ? this.statuses.join(',') : null,
        creditStatus: this.creditStatus || null,
        deliveryStatus: this.deliveryStatus || null,
        billingStatus: this.billingStatus || null,
        customerId: this.customerId
      }
    });
  }
}
