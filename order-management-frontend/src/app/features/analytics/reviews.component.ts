import { CUSTOM_ELEMENTS_SCHEMA, Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Review, ReviewsService } from '../../core/reviews.service';
import { Ui5ValueAccessorDirective } from '../../shared/ui5-value-accessor.directive';

/** Semantische Suche in Kundenbewertungen (pgvector, Cosine-Similarity). */
@Component({
  selector: 'om-reviews',
  imports: [FormsModule, RouterLink, Ui5ValueAccessorDirective, CurrencyPipe, DatePipe, DecimalPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './reviews.component.html',
  styles: `
    .search { display: flex; gap: .75rem; align-items: flex-end; flex-wrap: wrap; }
    .search ui5-input { flex: 1; min-width: 18rem; }
    .examples { display: flex; gap: .5rem; flex-wrap: wrap; margin-top: .75rem; }
    .cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(20rem, 1fr)); gap: .75rem; }
    .card { padding: 1rem; border-radius: .75rem; border: 1px solid var(--sapGroup_ContentBorderColor);
      background: var(--sapGroup_ContentBackground); display: flex; flex-direction: column; gap: .5rem; }
    .card-head { display: flex; justify-content: space-between; align-items: center; }
    .comment { margin: 0; line-height: 1.45; }
    .keywords { display: flex; gap: .25rem; flex-wrap: wrap; }
  `
})
export class ReviewsComponent {
  private readonly api = inject(ReviewsService);

  protected query = '';
  protected limit = '50';
  protected readonly examples = ['Qualität schlecht', 'schnelle Lieferung', 'kaputt angekommen', 'tolles Preis-Leistungs-Verhältnis', 'Akku hält nicht lange'];

  protected readonly reviews = signal<Review[]>([]);
  protected readonly loading = signal(false);
  protected readonly searched = signal(false);
  protected readonly failed = signal(false);

  protected readonly average = computed(() => {
    const r = this.reviews();
    return r.length ? r.reduce((s, x) => s + x.rating, 0) / r.length : 0;
  });

  protected readonly sentiment = computed(() => {
    const a = this.average();
    return a >= 4 ? { text: 'Positiv', design: 'Positive' } : a >= 2.5 ? { text: 'Neutral', design: 'Neutral' } : { text: 'Negativ', design: 'Negative' };
  });

  protected readonly topWords = computed(() => {
    const stop = new Set(['nicht', 'aber', 'sehr', 'eine', 'einen', 'einem', 'einer', 'dass', 'auch', 'noch', 'schon', 'wirklich', 'diese', 'dieser', 'dieses', 'habe', 'hatte', 'wurde', 'werden', 'kann', 'mich', 'mein', 'meine', 'sind', 'ist', 'war', 'wie', 'und', 'oder', 'mit', 'für', 'das', 'der', 'die', 'den', 'dem', 'des', 'nach', 'beim', 'immer', 'ganz', 'total', 'etwas']);
    const count = new Map<string, number>();
    for (const r of this.reviews()) {
      for (const word of r.comment.toLowerCase().split(/[^a-zäöüß]+/)) {
        if (word.length > 3 && !stop.has(word)) count.set(word, (count.get(word) ?? 0) + 1);
      }
    }
    return [...count.entries()].sort((a, b) => b[1] - a[1]).slice(0, 8).map(([w]) => w);
  });

  protected useExample(text: string): void {
    this.query = text;
    this.search();
  }

  protected search(): void {
    if (!this.query.trim()) return;
    this.loading.set(true);
    this.searched.set(true);
    this.failed.set(false);
    this.api.getSimilarReviews(this.query.trim(), Number(this.limit)).subscribe({
      next: data => {
        this.reviews.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.reviews.set([]);
        this.failed.set(true);
        this.loading.set(false);
      }
    });
  }
}
