import { CUSTOM_ELEMENTS_SCHEMA, ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CodeText } from '../core/models';
import { semanticOf } from './status';

/** Statuskennzeichen mit semantischer Farbe (Fiori Object Status). */
@Component({
  selector: 'om-status',
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (status(); as s) {
      <ui5-tag [attr.design]="design()" [attr.title]="title()" wrapping-type="None">{{ text() }}</ui5-tag>
    }
  `
})
export class StatusTagComponent {
  readonly status = input<CodeText | null | undefined>();
  readonly label = input<string>();

  protected readonly design = computed(() => semanticOf(this.status()));
  protected readonly text = computed(() => this.status()?.text ?? '');
  protected readonly title = computed(() => (this.label() ? `${this.label()}: ${this.text()}` : this.text()));
}
