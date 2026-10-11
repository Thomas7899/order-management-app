import { CUSTOM_ELEMENTS_SCHEMA, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { InventoryApi } from '../../core/master-data-api.service';
import { Delivery, Warehouse } from '../../core/models';
import { DeliveriesApi } from '../../core/sales-api.service';
import { StatusTagComponent } from '../../shared/status-tag.component';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';

const PAGE_SIZE = 25;

@Component({
  selector: 'om-delivery-list',
  imports: [FormsModule, Ui5ValueAccessorDirective, StatusTagComponent, DatePipe, DecimalPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './delivery-list.component.html'
})
export class DeliveryListComponent implements OnInit {
  private readonly api = inject(DeliveriesApi);
  private readonly inventoryApi = inject(InventoryApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly statusOptions = [
    { code: '', text: 'Alle' },
    { code: 'CREATED', text: 'Angelegt (ohne Warenausgang)' },
    { code: 'GOODS_ISSUED', text: 'Warenausgang gebucht' },
    { code: 'CANCELLED', text: 'Storniert' }
  ];

  protected readonly billingOptions = [
    { code: '', text: 'Alle' },
    { code: 'NOT_PROCESSED', text: 'Nicht fakturiert' },
    { code: 'FULLY_PROCESSED', text: 'Fakturiert' }
  ];

  protected search = '';
  protected status = '';
  protected billingStatus = '';
  protected warehouseId = '';

  protected readonly warehouses = signal<Warehouse[]>([]);
  protected readonly rows = signal<Delivery[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  private page = 0;

  ngOnInit(): void {
    const q = this.route.snapshot.queryParamMap;
    this.search = q.get('search') ?? '';
    this.status = q.get('status') ?? '';
    this.billingStatus = q.get('billingStatus') ?? '';
    this.inventoryApi.warehouses().subscribe(w => this.warehouses.set(w));
    this.go();
  }

  protected go(): void {
    this.page = 0;
    this.rows.set([]);
    this.router.navigate([], {
      relativeTo: this.route, replaceUrl: true,
      queryParams: { search: this.search || null, status: this.status || null, billingStatus: this.billingStatus || null }
    });
    this.load();
  }

  protected reset(): void {
    this.search = '';
    this.status = '';
    this.billingStatus = '';
    this.warehouseId = '';
    this.go();
  }

  protected more(): void {
    this.page++;
    this.load();
  }

  protected open(event: Event): void {
    const key = (event as CustomEvent<{ row: { rowKey: string } }>).detail.row.rowKey;
    this.router.navigate(['/deliveries', key]);
  }

  private load(): void {
    this.loading.set(true);
    this.api.search({
      search: this.search, status: this.status, billingStatus: this.billingStatus,
      warehouseId: this.warehouseId ? Number(this.warehouseId) : undefined,
      page: this.page, size: PAGE_SIZE
    }).subscribe({
      next: page => {
        this.rows.update(rows => [...rows, ...page.content]);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }
}
