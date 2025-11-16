// order-management-frontend/src/app/dashboard/dashboard.component.ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DashboardService, DashboardStats, RecentActivity } from '../services/dashboard.service';
import { environment } from '../../environments/environment';
import { map } from 'rxjs/operators';

@Component({
    selector: 'app-dashboard',
    imports: [CommonModule],
    templateUrl: './dashboard.component.html',
    styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  stats: DashboardStats | null = null;
  recentActivity: RecentActivity | null = null;
  loading = true;
  error: string | null = null;

  constructor(private dashboardService: DashboardService) {}

  ngOnInit(): void {
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    this.loading = true;
    this.error = null;

    this.dashboardService.getDashboardStats().subscribe({
      next: (stats) => {
        this.stats = stats;
        this.checkLoadingComplete();
      },
      error: (error) => {
        this.error = 'Fehler beim Laden der Dashboard-Statistiken';
        this.loading = false;
        console.error('Dashboard Stats Error:', error);
      }
    });

    // Recent Activity laden
    this.dashboardService.getRecentActivity().pipe(
      map(activity => {
        if (activity && activity.lowStockProducts) {
          const backendUrl = environment.apiUrl.replace('/api', '');
          activity.lowStockProducts.forEach(product => {
            if (product.imageUrl) {
              product.imageUrl = `${backendUrl}/api${product.imageUrl}`;
            }
          });
        }
        return activity;
      })
    ).subscribe({
      next: (activity) => {
        this.recentActivity = activity;
        this.checkLoadingComplete();
      },
      error: (error) => {
        this.error = 'Fehler beim Laden der aktuellen Aktivitäten';
        this.loading = false;
        console.error('Recent Activity Error:', error);
      }
    });
  }

  private checkLoadingComplete(): void {
    if (this.stats && this.recentActivity) {
      this.loading = false;
    }
  }

  getOrderStatusDisplayName(status: string): string {
    const statusMap: { [key: string]: string } = {
      'PENDING': 'Ausstehend',
      'CONFIRMED': 'Bestätigt', 
      'PROCESSING': 'In Bearbeitung',
      'SHIPPED': 'Versandt',
      'DELIVERED': 'Geliefert',
      'CANCELLED': 'Storniert'
    };
    return statusMap[status] || status;
  }

  getStatusColor(status: string): string {
    const colorMap: { [key: string]: string } = {
      'PENDING': 'orange',
      'CONFIRMED': 'blue',
      'PROCESSING': 'purple',
      'SHIPPED': 'teal',
      'DELIVERED': 'green',
      'CANCELLED': 'red'
    };
    return colorMap[status] || 'gray';
  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('de-DE', {
      style: 'currency',
      currency: 'EUR'
    }).format(amount);
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleDateString('de-DE', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  refresh(): void {
    this.loadDashboardData();
  }

}