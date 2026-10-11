import { CUSTOM_ELEMENTS_SCHEMA, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DecimalPipe, PercentPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { MaterialsApi } from '../../core/master-data-api.service';
import { Material } from '../../core/models';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';
import { MaterialDialogComponent } from './material-dialog.component';

const PAGE_SIZE = 30;

@Component({
  selector: 'om-material-list',
  imports: [FormsModule, Ui5ValueAccessorDirective, MaterialDialogComponent, CurrencyPipe, DecimalPipe, PercentPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './material-list.component.html',
  styles: `.thumb { width: 2.5rem; height: 2.5rem; border-radius: .5rem; object-fit: cover; background: var(--sapNeutralBackgroundColor); }
           .material { display: flex; gap: .75rem; align-items: center; }`
})
export class MaterialListComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(MaterialsApi);
  private readonly router = inject(Router);

  protected readonly all = signal<Material[]>([]);
  protected readonly loading = signal(false);
  protected readonly dialogOpen = signal(false);
  protected readonly visibleCount = signal(PAGE_SIZE);

  protected readonly search = signal('');
  protected readonly category = signal('');
  protected readonly stock = signal('');
  protected readonly showBlocked = signal(false);

  protected readonly categories = computed(() => [...new Set(this.all().map(m => m.category).filter(Boolean) as string[])].sort());

  protected readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    return this.all().filter(m =>
      (!q || [m.materialNumber, m.name, m.description ?? ''].some(v => v.toLowerCase().includes(q)))
      && (!this.category() || m.category === this.category())
      && (this.showBlocked() || m.active)
      && (this.stock() !== 'none' || m.stockQuantity === 0)
      && (this.stock() !== 'available' || m.stockQuantity > 0));
  });

  protected readonly rows = computed(() => this.filtered().slice(0, this.visibleCount()));

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: list => {
        this.all.set(list.sort((a, b) => a.materialNumber.localeCompare(b.materialNumber)));
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  protected margin(m: Material): number | null {
    return m.costPrice && m.price ? (m.price - m.costPrice) / m.price : null;
  }

  protected filterChanged(): void {
    this.visibleCount.set(PAGE_SIZE);
  }

  protected more(): void {
    this.visibleCount.update(n => n + PAGE_SIZE);
  }

  protected open(event: Event): void {
    this.router.navigate(['/materials', (event as CustomEvent<{ row: { rowKey: string } }>).detail.row.rowKey]);
  }

  protected saved(m: Material): void {
    this.dialogOpen.set(false);
    this.router.navigate(['/materials', m.id]);
  }
}
