// order-management-frontend/src/app/app.component.ts
import { CUSTOM_ELEMENTS_SCHEMA, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { BackendStatusService } from './core/backend-status.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  template: `
    @if (backend.starting()) {
      <div class="backend-starting" role="status">
        <ui5-message-strip design="Information" hide-close-button>
          Der Server wird gerade gestartet. Nach einer längeren Pause dauert das ein bis zwei Minuten.
        </ui5-message-strip>
      </div>
    }
    <router-outlet />
  `,
  styles: `
    .backend-starting {
      position: fixed;
      top: 0.75rem;
      left: 50%;
      transform: translateX(-50%);
      width: min(36rem, calc(100% - 2rem));
      z-index: 1000;
      box-shadow: var(--sapContent_Shadow1, 0 0.25rem 1rem rgba(0, 0, 0, 0.15));
      border-radius: 0.5rem;
    }
  `
})
export class AppComponent {
  protected readonly backend = inject(BackendStatusService);

  constructor() {
    // Sofort beim Öffnen anstoßen, nicht erst mit dem ersten fachlichen Aufruf
    this.backend.wake();
  }
}
