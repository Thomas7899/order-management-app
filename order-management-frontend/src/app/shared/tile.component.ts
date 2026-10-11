import { CUSTOM_ELEMENTS_SCHEMA, ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Semantic } from './status';

/** Launchpad-Kachel mit Kennzahl (angelehnt an die Fiori KPI-/Generic Tile). */
@Component({
  selector: 'om-tile',
  imports: [RouterLink],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="tile" [routerLink]="link()" [queryParams]="query()" [attr.aria-label]="title() + (value() !== null && value() !== undefined ? ': ' + value() + ' ' + (unit() ?? '') : '')">
      <div class="head">
        <div class="title">{{ title() }}</div>
        @if (subtitle()) {
          <div class="subtitle">{{ subtitle() }}</div>
        }
      </div>
      <div class="body">
        @if (value() !== null && value() !== undefined) {
          <span class="value" [attr.data-state]="state()">{{ value() }}</span>
          @if (unit()) {
            <span class="unit">{{ unit() }}</span>
          }
        }
        <ui5-icon class="icon" [attr.name]="icon()"></ui5-icon>
      </div>
      <div class="footer">{{ footer() }}</div>
    </a>
  `,
  styles: `
    :host { display: block; }
    .tile {
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      width: 11rem;
      height: 11rem;
      padding: 1rem;
      border-radius: var(--sapTile_BorderCornerRadius, 1rem);
      background: var(--sapTile_Background, #fff);
      border: 1px solid var(--sapTile_BorderColor, transparent);
      box-shadow: var(--sapContent_Shadow0, 0 0 0.125rem rgba(0,0,0,.16), 0 0.125rem 0.25rem rgba(0,0,0,.16));
      color: var(--sapTile_TextColor, var(--sapTextColor));
      text-decoration: none;
      transition: box-shadow .15s ease, transform .15s ease;
    }
    .tile:hover {
      background: var(--sapTile_Hover_Background, var(--sapTile_Background));
      box-shadow: var(--sapContent_Shadow2, 0 0.5rem 1rem rgba(0,0,0,.16));
    }
    .tile:focus-visible {
      outline: var(--sapContent_FocusWidth, .125rem) solid var(--sapContent_FocusColor, #0070f2);
      outline-offset: 2px;
    }
    .title {
      font-family: var(--sapFontSemiboldDuplexFamily, var(--sapFontFamily));
      font-size: var(--sapFontLargeSize, 1rem);
      color: var(--sapTile_TitleTextColor, var(--sapTextColor));
      line-height: 1.25;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
    .subtitle {
      margin-top: .25rem;
      font-size: var(--sapFontSize, .875rem);
      color: var(--sapTile_TextColor, var(--sapContent_LabelColor));
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .body {
      display: flex;
      align-items: baseline;
      gap: .25rem;
      min-height: 2.5rem;
    }
    .value {
      font-family: var(--sapFontLightFamily, var(--sapFontFamily));
      font-size: 2.25rem;
      line-height: 1;
      color: var(--sapNeutralTextColor, var(--sapTextColor));
    }
    .value[data-state="Positive"] { color: var(--sapPositiveTextColor); }
    .value[data-state="Negative"] { color: var(--sapNegativeTextColor); }
    .value[data-state="Critical"] { color: var(--sapCriticalTextColor); }
    .value[data-state="Information"] { color: var(--sapInformativeTextColor, var(--sapBrandColor)); }
    .unit {
      font-size: var(--sapFontSize, .875rem);
      color: var(--sapContent_LabelColor);
    }
    .icon {
      margin-left: auto;
      width: 2rem;
      height: 2rem;
      color: var(--sapTile_IconColor, var(--sapContent_IconColor));
      align-self: flex-end;
    }
    .footer {
      font-size: var(--sapFontSmallSize, .75rem);
      color: var(--sapTile_TextColor, var(--sapContent_LabelColor));
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  `
})
export class TileComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
  readonly value = input<string | number | null>();
  readonly unit = input<string>();
  readonly footer = input<string>('');
  readonly icon = input<string>('business-objects-experience');
  readonly state = input<Semantic>('Neutral');
  readonly link = input.required<string>();
  readonly query = input<Record<string, string> | null>(null);
}
