import { CUSTOM_ELEMENTS_SCHEMA, Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { ReviewsService, SearchResult } from '../../core/reviews.service';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';

/** Semantische Suche in Kundenbewertungen (OpenAI-Embeddings, pgvector, Kosinus-Ähnlichkeit). */
@Component({
  selector: 'om-reviews',
  imports: [FormsModule, RouterLink, Ui5ValueAccessorDirective, DatePipe, DecimalPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './reviews.component.html',
  styles: `
    .search { display: flex; gap: .75rem; align-items: flex-end; flex-wrap: wrap; }
    .search ui5-input { flex: 1; min-width: 18rem; }
    .examples { display: flex; gap: .5rem; flex-wrap: wrap; margin-top: .75rem; align-items: center; }
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 20rem; gap: 1rem; align-items: start; }
    @media (max-width: 1100px) { .layout { grid-template-columns: minmax(0, 1fr); } }
    .cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(20rem, 1fr)); gap: .75rem; }
    .card { padding: 1rem; border-radius: .75rem; border: 1px solid var(--sapGroup_ContentBorderColor);
      background: var(--sapGroup_ContentBackground); display: flex; flex-direction: column; gap: .5rem; }
    .card-head { display: flex; justify-content: space-between; align-items: center; gap: .5rem; }
    .comment { margin: 0; line-height: 1.45; }
    .tags { display: flex; gap: .25rem; flex-wrap: wrap; }
    .product-row { display: flex; justify-content: space-between; gap: .5rem; padding: .4rem 0;
      border-bottom: 1px solid var(--sapList_BorderColor); }
  `
})
export class ReviewsComponent {
  private readonly api = inject(ReviewsService);

  protected query = '';
  protected limit = '30';
  protected readonly examples = ['Ware kam beschädigt an', 'Kundendienst antwortet nicht', 'Aufbau war schwierig',
    'hält nicht lange', 'gutes Preis-Leistungs-Verhältnis', 'Verbindung zur App bricht ab'];

  protected readonly result = signal<SearchResult | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Häufigste Themen unter den Treffern (aus der gespeicherten Einordnung der Bewertungen). */
  protected readonly topThemes = computed(() => {
    const counts = new Map<string, number>();
    for (const hit of this.result()?.hits ?? []) {
      for (const theme of hit.themes) counts.set(theme, (counts.get(theme) ?? 0) + 1 + hit.duplicates);
    }
    return [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5);
  });

  protected readonly average = computed(() => {
    const hits = this.result()?.hits ?? [];
    const weight = hits.reduce((s, h) => s + 1 + h.duplicates, 0);
    return weight ? hits.reduce((s, h) => s + h.rating * (1 + h.duplicates), 0) / weight : 0;
  });

  protected useExample(text: string): void {
    this.query = text;
    this.search();
  }

  protected search(): void {
    if (!this.query.trim()) return;
    this.loading.set(true);
    this.error.set(null);
    this.api.search(this.query.trim(), Number(this.limit)).subscribe({
      next: data => {
        this.result.set(data);
        this.loading.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.result.set(null);
        this.error.set((e.error as { detail?: string })?.detail ?? 'Die semantische Suche ist fehlgeschlagen.');
        this.loading.set(false);
      }
    });
  }

  protected sentimentDesign(s: string | null): string {
    return ({ POSITIVE: 'Positive', NEUTRAL: 'Neutral', NEGATIVE: 'Negative' } as Record<string, string>)[s ?? ''] ?? 'Neutral';
  }

  protected sentimentText(s: string | null): string {
    return ({ POSITIVE: 'Positiv', NEUTRAL: 'Neutral', NEGATIVE: 'Negativ' } as Record<string, string>)[s ?? ''] ?? '';
  }
}
