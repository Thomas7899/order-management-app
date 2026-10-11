import { CUSTOM_ELEMENTS_SCHEMA, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { MessageService } from '../../core/message.service';
import { InventoryApi } from '../../core/master-data-api.service';
import { StockMovement, Warehouse } from '../../core/models';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';
import { MovementDialogComponent } from './movement-dialog.component';

const PAGE_SIZE = 30;

/** Materialbelegliste (angelehnt an MB51) mit Storno. */
@Component({
  selector: 'om-movement-list',
  imports: [FormsModule, RouterLink, Ui5ValueAccessorDirective, MovementDialogComponent, DatePipe, DecimalPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './movement-list.component.html'
})
export class MovementListComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(InventoryApi);
  private readonly messages = inject(MessageService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly codes = [
    { code: '101', text: '101 Wareneingang' }, { code: '102', text: '102 Storno WE' },
    { code: '201', text: '201 Warenausgang Kostenstelle' }, { code: '311', text: '311 Umlagerung' },
    { code: '551', text: '551 Verschrottung' }, { code: '561', text: '561 Initialer Bestand' },
    { code: '601', text: '601 Warenausgang Lieferung' }, { code: '602', text: '602 Storno WA Lieferung' },
    { code: '651', text: '651 Retoure' }, { code: '701', text: '701 Inventur Mehrmenge' },
    { code: '702', text: '702 Inventur Fehlmenge' }
  ];

  protected search = '';
  protected movementCodes: string[] = [];
  protected warehouseId = '';
  protected dateFrom = '';
  protected dateTo = '';

  protected readonly warehouses = signal<Warehouse[]>([]);
  protected readonly rows = signal<StockMovement[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);
  protected readonly dialogOpen = signal(false);
  protected readonly reverseCandidate = signal<StockMovement | null>(null);
  private page = 0;

  ngOnInit(): void {
    this.search = this.route.snapshot.queryParamMap.get('search') ?? '';
    this.api.warehouses().subscribe(w => this.warehouses.set(w));
    this.go();
  }

  protected codeSelection(event: Event): void {
    this.movementCodes = (event as CustomEvent<{ items: HTMLElement[] }>).detail.items.map(i => i.dataset['code']!);
  }

  protected go(): void {
    this.page = 0;
    this.rows.set([]);
    this.router.navigate([], { relativeTo: this.route, replaceUrl: true, queryParams: { search: this.search || null } });
    this.load();
  }

  protected reset(): void {
    this.search = '';
    this.movementCodes = [];
    this.warehouseId = '';
    this.dateFrom = '';
    this.dateTo = '';
    this.go();
  }

  protected more(): void {
    this.page++;
    this.load();
  }

  protected canReverse(m: StockMovement): boolean {
    return !m.reversed && !m.reversedMovementNumber && m.movementCode !== '601' && this.auth.hasAnyRole('WAREHOUSE');
  }

  protected reverse(): void {
    const m = this.reverseCandidate();
    if (!m) return;
    this.api.reverseMovement(m.id).subscribe(reversal => {
      this.reverseCandidate.set(null);
      this.messages.success(`Stornobeleg ${reversal.movementNumber} gebucht (Bewegungsart ${reversal.movementCode})`);
      this.go();
    });
  }

  protected posted(): void {
    this.dialogOpen.set(false);
    this.go();
  }

  private load(): void {
    this.loading.set(true);
    this.api.movements({
      search: this.search, movementCode: this.movementCodes,
      warehouseId: this.warehouseId ? Number(this.warehouseId) : undefined,
      dateFrom: this.dateFrom, dateTo: this.dateTo, page: this.page, size: PAGE_SIZE
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
