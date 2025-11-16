import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService } from './toast.service';

@Component({
    selector: 'app-toast',
    imports: [CommonModule],
    template: `
    <div *ngIf="message" class="toast">{{ message }}</div>
  `,
    styles: [`
    .toast {
      position: fixed;
      bottom: 20px;
      right: 20px;
      background: #1f8a49;
      padding: 10px 18px;
      color: white;
      border-radius: 6px;
      animation: fadeOut 3s forwards;
    }
    @keyframes fadeOut {
      0% { opacity: 1; }
      70% { opacity: 1; }
      100% { opacity: 0; }
    }
  `]
})
export class ToastComponent {
  message: string | null = null;

  constructor(toast: ToastService) {
    toast.message$.subscribe(m => {
      this.message = m;
      setTimeout(() => this.message = null, 3000);
    });
  }
}
