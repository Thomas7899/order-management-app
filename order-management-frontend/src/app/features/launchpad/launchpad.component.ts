import { CUSTOM_ELEMENTS_SCHEMA, Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ReviewInsightsService } from '../../core/review-insights.service';
import { ReviewsService } from '../../core/reviews.service';
import { OrderRequestsApi } from '../../core/order-requests.service';
import { AuthService } from '../../core/auth.service';
import { DashboardApi } from '../../core/analytics-api.service';
import { DashboardStats, RecentActivity } from '../../core/models';
import { TileComponent } from '../../shared/tile.component';
import { StatusTagComponent } from '../../shared/status-tag.component';
import { STOCK_STATUS_TEXT, semanticOf } from '../../shared/status';

@Component({
  selector: 'om-launchpad',
  imports: [TileComponent, StatusTagComponent, CurrencyPipe, DatePipe, DecimalPipe],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './launchpad.component.html',
  styleUrl: './launchpad.component.css'
})
export class LaunchpadComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(DashboardApi);
  protected readonly router = inject(Router);
  protected readonly today = new Date();

  protected readonly stats = signal<DashboardStats | null>(null);
  protected readonly activity = signal<RecentActivity | null>(null);
  protected readonly qualityAlerts = signal<number | null>(null);
  protected readonly openCritical = signal<number | null>(null);
  protected readonly openRequests = signal<number | null>(null);
  private readonly requestsApi = inject(OrderRequestsApi);
  private readonly insightsApi = inject(ReviewInsightsService);
  private readonly reviewsApi = inject(ReviewsService);
  protected readonly loading = signal(true);
  protected readonly stockStatusText = STOCK_STATUS_TEXT;
  protected readonly semanticOf = semanticOf;

  protected readonly greeting = computed(() => {
    const hour = new Date().getHours();
    const prefix = hour < 11 ? 'Guten Morgen' : hour < 18 ? 'Guten Tag' : 'Guten Abend';
    const name = this.auth.user()?.fullName;
    return name ? `${prefix}, ${name}` : prefix;
  });

  protected readonly openOrders = computed(() => {
    const s = this.stats();
    return s ? (s.ordersByStatus['OPEN'] ?? 0) + (s.ordersByStatus['IN_PROCESS'] ?? 0) : null;
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    // Kundenbewertungen: auffällige Materialien und unbeantwortete Kritik (Fehler hier sollen das Launchpad nicht stören)
    this.insightsApi.qualityAlerts().subscribe({ next: a => this.qualityAlerts.set(a.length), error: () => undefined });
    this.reviewsApi.openCritical(30).subscribe({ next: o => this.openCritical.set(o.count), error: () => undefined });
    this.requestsApi.countOpen().subscribe({ next: c => this.openRequests.set(c.open), error: () => undefined });
    forkJoin({ stats: this.api.stats(), activity: this.api.recentActivity() }).subscribe({
      next: ({ stats, activity }) => {
        this.stats.set(stats);
        this.activity.set(activity);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  protected compact(value: number | null | undefined): string {
    if (value === null || value === undefined) return '–';
    if (Math.abs(value) >= 1_000_000) return (value / 1_000_000).toLocaleString('de-DE', { maximumFractionDigits: 1 });
    if (Math.abs(value) >= 1_000) return (value / 1_000).toLocaleString('de-DE', { maximumFractionDigits: 1 });
    return value.toLocaleString('de-DE', { maximumFractionDigits: 0 });
  }

  protected compactUnit(value: number | null | undefined): string {
    if (value === null || value === undefined) return '';
    if (Math.abs(value) >= 1_000_000) return 'Mio. €';
    if (Math.abs(value) >= 1_000) return 'Tsd. €';
    return '€';
  }

  protected openOrder(event: Event): void {
    const key = (event as CustomEvent<{ row: { rowKey: string } }>).detail.row.rowKey;
    this.router.navigate(['/orders', key]);
  }

  protected openMaterial(event: Event): void {
    const key = (event as CustomEvent<{ row: { rowKey: string } }>).detail.row.rowKey;
    this.router.navigate(['/materials', key]);
  }
}
