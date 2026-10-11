import { CUSTOM_ELEMENTS_SCHEMA, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { InventoryApi } from '../../core/master-data-api.service';
import { Warehouse, WarehouseStock } from '../../core/models';
import { STOCK_STATUS_TEXT, semanticOf } from '../../shared/status';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';
import { MovementDialogComponent } from './movement-dialog.component';

/** Bestandsübersicht je Lagerort (angelehnt an MMBE/MB52). */
@Component({
  selector: 'om-stock-overview',
  imports: [FormsModule, Ui5ValueAccessorDirective, MovementDialogComponent, CurrencyPipe, DatePipe, DecimalPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './stock-overview.component.html',
  styles: `
    .locations { display: grid; grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr)); gap: .75rem; }
    .location { cursor: pointer; padding: 1rem; border-radius: .75rem; border: 1px solid var(--sapGroup_ContentBorderColor);
      background: var(--sapTile_Background, var(--sapGroup_ContentBackground)); display: flex; flex-direction: column; gap: .25rem; }
    .location:hover { box-shadow: var(--sapContent_Shadow1); }
    .location.selected { border: 2px solid var(--sapSelectedColor, var(--sapBrandColor)); }
    .location .value { font-size: 1.5rem; font-family: var(--sapFontLightFamily, var(--sapFontFamily)); }
    .location-head { display: flex; justify-content: space-between; align-items: center; gap: .5rem; }
  `
})
export class StockOverviewComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(InventoryApi);
  private readonly router = inject(Router);

  protected readonly warehouses = signal<Warehouse[]>([]);
  protected readonly selected = signal<Warehouse | null>(null);
  protected readonly stock = signal<WarehouseStock[]>([]);
  protected readonly loading = signal(false);
  protected readonly dialogOpen = signal(false);
  protected readonly stockStatusText = STOCK_STATUS_TEXT;
  protected readonly semanticOf = semanticOf;

  protected readonly search = signal('');
  protected readonly status = signal('');

  protected readonly totalValue = computed(() => this.warehouses().reduce((s, w) => s + (w.inventoryValue ?? 0), 0));

  protected readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.stock()
      .filter(s => !q || [s.materialNumber, s.productName, s.binLocation ?? ''].some(v => v.toLowerCase().includes(q)))
      .filter(s => !this.status() || s.stockStatus === this.status())
      .sort((a, b) => a.materialNumber.localeCompare(b.materialNumber));
  });

  ngOnInit(): void {
    this.loadWarehouses();
  }

  protected loadWarehouses(): void {
    this.api.warehouses().subscribe(list => {
      const sorted = list.sort((a, b) => a.code.localeCompare(b.code));
      this.warehouses.set(sorted);
      const current = this.selected();
      this.select(sorted.find(w => w.id === current?.id) ?? sorted.find(w => w.isDefault) ?? sorted[0]);
    });
  }

  protected select(warehouse: Warehouse | undefined): void {
    if (!warehouse) return;
    this.selected.set(warehouse);
    this.loading.set(true);
    this.api.stockByWarehouse(warehouse.id).subscribe({
      next: s => {
        this.stock.set(s);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  protected openMaterial(event: Event): void {
    this.router.navigate(['/materials', (event as CustomEvent<{ row: { rowKey: string } }>).detail.row.rowKey]);
  }

  protected posted(): void {
    this.dialogOpen.set(false);
    this.loadWarehouses();
  }
}
