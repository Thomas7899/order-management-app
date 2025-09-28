import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DashboardService, DashboardStats, RecentActivity } from '../services/dashboard.service';
import { OrderStatus } from '../services/order.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
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

    // Stats laden
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
    this.dashboardService.getRecentActivity().subscribe({
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

  getImageUrl(imageUrl: string | undefined): string {
    if (!imageUrl) {
      return 'http://localhost:8080/images/placeholder.jpg?t=' + Date.now();
    }
    
    // Verwende echte Bilder vom Backend mit Cache-Buster
    return 'http://localhost:8080' + imageUrl + '?t=' + Date.now();
  }
}