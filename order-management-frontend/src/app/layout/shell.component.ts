import { CUSTOM_ELEMENTS_SCHEMA, Component, ElementRef, computed, effect, inject, signal, viewChild } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { DatePipe } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { MessageService } from '../core/message.service';
import { applyTheme, loadTheme, ThemeName } from '../shared/ui5';

interface NavItem {
  text: string;
  icon: string;
  route: string;
  roles?: string[];
}

interface NavGroup {
  text: string;
  items: NavItem[];
}

@Component({
  selector: 'om-shell',
  imports: [RouterOutlet, DatePipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css'
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  protected readonly messages = inject(MessageService);
  private readonly router = inject(Router);

  private readonly userMenu = viewChild<ElementRef<HTMLElement & { open: boolean; opener: unknown }>>('userMenu');
  private readonly messagePopover = viewChild<ElementRef<HTMLElement & { open: boolean; opener: unknown }>>('messagePopover');
  private readonly toastRef = viewChild<ElementRef<HTMLElement & { open: boolean }>>('toast');

  protected readonly sideCollapsed = signal(window.innerWidth < 1024);
  protected readonly theme = signal<ThemeName>(loadTheme());

  protected readonly groups: NavGroup[] = [
    {
      text: 'Vertrieb',
      items: [
        { text: 'Kundenaufträge', icon: 'sales-order', route: '/orders' },
        { text: 'Auftrag anlegen', icon: 'add', route: '/orders/new', roles: ['SALES'] },
        { text: 'Debitoren', icon: 'customer', route: '/customers' }
      ]
    },
    {
      text: 'Logistik',
      items: [
        { text: 'Auslieferungen', icon: 'shipping-status', route: '/deliveries' },
        { text: 'Bestandsübersicht', icon: 'inventory', route: '/stock' },
        { text: 'Materialbelege', icon: 'log', route: '/movements' },
        { text: 'Materialien', icon: 'product', route: '/materials' }
      ]
    },
    {
      text: 'Fakturierung',
      items: [
        { text: 'Fakturavorrat', icon: 'pending', route: '/billing-due' },
        { text: 'Fakturen & offene Posten', icon: 'receipt', route: '/invoices' }
      ]
    },
    {
      text: 'Analyse',
      items: [
        { text: 'Kennzahlen', icon: 'kpi-corporate-performance', route: '/reporting' },
        { text: 'Bewertungsanalyse', icon: 'ai', route: '/review-analysis' },
        { text: 'Bewertungssuche', icon: 'feedback', route: '/reviews' },
        { text: 'Bestandsanalyse', icon: 'lightbulb', route: '/inventory-ai' }
      ]
    }
  ];

  private readonly url = toSignal(
    this.router.events.pipe(filter(e => e instanceof NavigationEnd), map(e => (e as NavigationEnd).urlAfterRedirects)),
    { initialValue: this.router.url }
  );

  /** Das Navigationselement mit dem längsten passenden Pfad ist ausgewählt. */
  protected readonly activeRoute = computed(() => {
    const path = this.url().split('?')[0];
    if (path === '/' || path === '') return '/';
    const candidates = [...this.groups.flatMap(g => g.items.map(i => i.route)), '/admin/users'];
    return candidates
      .filter(route => path === route || path.startsWith(route + '/'))
      .sort((a, b) => b.length - a.length)[0] ?? '';
  });

  protected readonly initials = computed(() => {
    const name = this.auth.user()?.fullName ?? '';
    return name.split(' ').map(p => p[0]).join('').slice(0, 2).toUpperCase();
  });

  protected readonly roleText = computed(() => this.auth.user()?.roles.map(r => r.text).join(', ') ?? '');

  protected readonly errorCount = computed(() => this.messages.log().filter(m => m.type === 'error' || m.type === 'warning').length);

  constructor() {
    effect(() => {
      const toast = this.messages.toast();
      const element = this.toastRef()?.nativeElement;
      if (toast && element) {
        element.open = false;
        queueMicrotask(() => (element.open = true));
      }
    });
  }

  protected visible(item: NavItem): boolean {
    return !item.roles || this.auth.hasAnyRole(...(item.roles as never[]));
  }

  protected navigate(event: Event): void {
    const item = (event as CustomEvent<{ item: HTMLElement }>).detail.item;
    const route = item?.dataset['route'];
    if (route) {
      this.router.navigateByUrl(route);
      if (window.innerWidth < 600) this.sideCollapsed.set(true);
    }
  }

  protected toggleSide(): void {
    this.sideCollapsed.update(v => !v);
  }

  protected openUserMenu(event: Event): void {
    const menu = this.userMenu()?.nativeElement;
    if (!menu) return;
    menu.opener = (event as CustomEvent<{ targetRef: HTMLElement }>).detail.targetRef;
    menu.open = true;
  }

  protected openMessages(event: Event): void {
    const popover = this.messagePopover()?.nativeElement;
    if (!popover) return;
    popover.opener = (event as CustomEvent<{ targetRef: HTMLElement }>).detail.targetRef;
    popover.open = true;
  }

  protected userMenuItem(event: Event): void {
    const item = (event as CustomEvent<{ item: HTMLElement }>).detail.item;
    switch (item.dataset['action']) {
      case 'theme': {
        const next: ThemeName = this.theme() === 'sap_horizon' ? 'sap_horizon_dark' : 'sap_horizon';
        applyTheme(next);
        this.theme.set(next);
        break;
      }
      case 'users':
        this.router.navigateByUrl('/admin/users');
        break;
    }
  }

  protected signOut(): void {
    this.auth.logout();
  }

  protected home(): void {
    this.router.navigateByUrl('/');
  }
}
