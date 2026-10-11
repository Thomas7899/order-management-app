import { CUSTOM_ELEMENTS_SCHEMA, Component, effect, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MessageService } from '../core/message.service';
import { Review, ReviewsService } from '../core/reviews.service';
import { Ui5ValueAccessorDirective } from './ui5-value-accessor.directive';

/**
 * Antwort auf eine Kundenbewertung: Vorschlag per KI (oder Vorlage ohne KI) zum Prüfen und Anpassen.
 * Gespeichert wird nur der Text, den der Mensch freigibt.
 */
@Component({
  selector: 'om-review-reply-dialog',
  imports: [FormsModule, Ui5ValueAccessorDirective, DatePipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  template: `
    <ui5-dialog [attr.header-text]="'Antwort auf Bewertung · ' + (review()?.productName ?? '')" [open]="open()" (close)="closed.emit()">
      @if (review(); as r) {
        <div class="review">
          <div class="head">
            <ui5-rating-indicator readonly size="S" [value]="r.rating"></ui5-rating-indicator>
            <span class="om-subtle">{{ r.customerName }} · {{ r.createdAt | date: 'dd.MM.yyyy' }}</span>
          </div>
          <p>„{{ r.comment }}“</p>
        </div>
        <div class="om-field">
          <div class="label-row">
            <ui5-label show-colon required>Antwort</ui5-label>
            @if (source() === 'KI') {
              <ui5-tag design="Information" hide-state-icon><ui5-icon slot="icon" name="ai"></ui5-icon>Vorschlag von KI – bitte prüfen</ui5-tag>
            } @else if (source() === 'REGELN') {
              <ui5-tag design="Neutral" hide-state-icon>Vorlage</ui5-tag>
            }
          </div>
          @if (loading()) {
            <ui5-busy-indicator active size="M" text="Antwortvorschlag wird erstellt …"></ui5-busy-indicator>
          } @else {
            <ui5-textarea [(ngModel)]="text" rows="8" growing maxlength="2000"></ui5-textarea>
          }
        </div>
      }
      <div slot="footer" class="om-dialog-footer">
        <ui5-button design="Transparent" icon="ai" [disabled]="loading()" (click)="suggest()">Neuer Vorschlag</ui5-button>
        <ui5-button design="Emphasized" [disabled]="busy() || loading() || !text.trim()" (click)="save()">Antwort speichern</ui5-button>
        <ui5-button design="Transparent" (click)="closed.emit()">Abbrechen</ui5-button>
      </div>
    </ui5-dialog>
  `,
  styles: `
    .review { padding: .75rem 1rem; border-radius: .5rem; background: var(--sapList_Background); margin-bottom: 1rem;
      border: 1px solid var(--sapList_BorderColor); max-width: 40rem; }
    .review p { margin: .5rem 0 0; line-height: 1.45; }
    .head, .label-row { display: flex; justify-content: space-between; align-items: center; gap: .5rem; }
    ui5-textarea { width: min(40rem, 80vw); }
  `
})
export class ReviewReplyDialogComponent {
  readonly open = input(false);
  readonly review = input<Review | null>(null);
  readonly saved = output<Review>();
  readonly closed = output<void>();

  private readonly api = inject(ReviewsService);
  private readonly messages = inject(MessageService);

  protected text = '';
  protected readonly source = signal<'KI' | 'REGELN' | null>(null);
  protected readonly loading = signal(false);
  protected readonly busy = signal(false);

  constructor() {
    effect(() => {
      const r = this.review();
      if (!this.open() || !r) return;
      this.text = r.replyText ?? '';
      this.source.set(null);
      // Bestehende Antwort zum Bearbeiten anzeigen, sonst direkt einen Vorschlag holen
      if (!r.replyText) this.suggest();
    });
  }

  protected suggest(): void {
    const r = this.review();
    if (!r) return;
    this.loading.set(true);
    this.api.suggestReply(r.id).subscribe({
      next: s => {
        this.text = s.text;
        this.source.set(s.source);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  protected save(): void {
    const r = this.review();
    if (!r) return;
    this.busy.set(true);
    this.api.reply(r.id, this.text.trim()).subscribe({
      next: saved => {
        this.busy.set(false);
        this.messages.success('Antwort gespeichert');
        this.saved.emit(saved);
      },
      error: () => this.busy.set(false)
    });
  }
}
