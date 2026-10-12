import { CUSTOM_ELEMENTS_SCHEMA, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import {
  CONFIDENCE_DESIGN, OVERALL_TEXT, OrderRequestDetail, OrderRequestListItem, OrderRequestsApi, senderName
} from '../../core/order-requests.service';
import { StatusTagComponent } from '../../shared/status-tag.component';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';
import { OrderRequestCreateDialogComponent } from './order-request-create-dialog.component';

/**
 * Auftragseingang (angelehnt an SAP „Kundenauftragsanforderungen verwalten“): eingegangene Bestellungen
 * mit Stand der Zuordnung, von hier aus prüfen und als Kundenauftrag übernehmen.
 */
@Component({
  selector: 'om-order-request-list',
  imports: [FormsModule, Ui5ValueAccessorDirective, StatusTagComponent, DatePipe, OrderRequestCreateDialogComponent],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './order-request-list.component.html'
})
export class OrderRequestListComponent implements OnInit {
  private readonly api = inject(OrderRequestsApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly auth = inject(AuthService);

  protected readonly statusOptions = [
    { code: 'OPEN', text: 'Offen' },
    { code: '', text: 'Alle' },
    { code: 'CONVERTED', text: 'Übernommen' },
    { code: 'REJECTED', text: 'Abgelehnt' }
  ];
  protected readonly confidenceText = OVERALL_TEXT;
  protected readonly senderName = senderName;
  protected readonly confidenceDesign = CONFIDENCE_DESIGN;

  protected status = 'OPEN';
  protected readonly rows = signal<OrderRequestListItem[]>([]);
  protected readonly loading = signal(false);
  protected readonly createOpen = signal(false);

  ngOnInit(): void {
    this.status = this.route.snapshot.queryParamMap.get('status') ?? 'OPEN';
    this.createOpen.set(this.route.snapshot.queryParamMap.get('create') === '1');
    this.go();
  }

  protected go(): void {
    this.router.navigate([], { relativeTo: this.route, replaceUrl: true, queryParams: { status: this.status || '' } });
    this.loading.set(true);
    this.api.list(this.status ? [this.status] : undefined).subscribe({
      next: rows => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  protected open(event: Event): void {
    const key = (event as CustomEvent<{ row: { rowKey: string } }>).detail.row.rowKey;
    this.router.navigate(['/order-requests', key]);
  }

  protected created(detail: OrderRequestDetail): void {
    this.createOpen.set(false);
    this.router.navigate(['/order-requests', detail.id]);
  }
}
