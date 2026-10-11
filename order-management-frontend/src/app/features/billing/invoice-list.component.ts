import { CUSTOM_ELEMENTS_SCHEMA, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Invoice } from '../../core/models';
import { InvoicesApi } from '../../core/sales-api.service';
import { StatusTagComponent } from '../../shared/status-tag.component';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';

const PAGE_SIZE = 25;

@Component({
  selector: 'om-invoice-list',
  imports: [FormsModule, Ui5ValueAccessorDirective, StatusTagComponent, CurrencyPipe, DatePipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './invoice-list.component.html',
  styles: `.segment { margin-bottom: .25rem; }`
})
export class InvoiceListComponent implements OnInit {
  private readonly api = inject(InvoicesApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly statusOptions = [
    { code: '', text: 'Alle' },
    { code: 'OPEN', text: 'Offen' },
    { code: 'PAID', text: 'Ausgeglichen' },
    { code: 'CANCELLED', text: 'Storniert' }
  ];

  protected readonly typeOptions = [
    { code: '', text: 'Alle' },
    { code: 'F2', text: 'F2 Rechnung' },
    { code: 'S1', text: 'S1 Stornorechnung' }
  ];

  protected search = '';
  protected status = '';
  protected billingType = '';
  protected overdue = false;
  protected dateFrom = '';
  protected dateTo = '';

  protected readonly rows = signal<Invoice[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  private page = 0;

  protected readonly view = computed(() => (this.overdueSignal() ? 'overdue' : this.statusSignal() === 'OPEN' ? 'open' : 'all'));
  private readonly overdueSignal = signal(false);
  private readonly statusSignal = signal('');

  ngOnInit(): void {
    const q = this.route.snapshot.queryParamMap;
    this.search = q.get('search') ?? '';
    this.status = q.get('status') ?? '';
    this.overdue = q.get('overdue') === 'true';
    this.go();
  }

  protected setView(event: Event): void {
    const key = (event as CustomEvent<{ selectedItems: HTMLElement[] }>).detail.selectedItems[0]?.dataset['key'];
    this.overdue = key === 'overdue';
    this.status = key === 'open' ? 'OPEN' : '';
    this.go();
  }

  protected go(): void {
    this.page = 0;
    this.rows.set([]);
    this.overdueSignal.set(this.overdue);
    this.statusSignal.set(this.status);
    this.router.navigate([], {
      relativeTo: this.route, replaceUrl: true,
      queryParams: { search: this.search || null, status: this.status || null, overdue: this.overdue ? 'true' : null }
    });
    this.load();
  }

  protected reset(): void {
    this.search = '';
    this.status = '';
    this.billingType = '';
    this.overdue = false;
    this.dateFrom = '';
    this.dateTo = '';
    this.go();
  }

  protected more(): void {
    this.page++;
    this.load();
  }

  protected open(event: Event): void {
    const key = (event as CustomEvent<{ row: { rowKey: string } }>).detail.row.rowKey;
    this.router.navigate(['/invoices', key]);
  }

  private load(): void {
    this.loading.set(true);
    this.api.search({
      search: this.search, status: this.status, billingType: this.billingType,
      overdue: this.overdue || undefined, dateFrom: this.dateFrom, dateTo: this.dateTo,
      page: this.page, size: PAGE_SIZE, sort: this.overdue ? 'dueDate,asc' : 'billingDate,desc'
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
