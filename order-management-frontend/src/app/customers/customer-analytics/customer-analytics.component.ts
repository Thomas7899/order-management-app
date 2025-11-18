// order-management-frontend/src/app/customers/customer-analytics/customer-analytics.component.ts
import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Customer } from '../../services/customer.service';
import { Order } from '../../services/order.service';
import { NgxEchartsModule } from 'ngx-echarts';
import { EChartsOption } from 'echarts';

@Component({
  selector: 'app-customer-analytics',
  standalone: true,
  imports: [CommonModule, NgxEchartsModule],
  templateUrl: './customer-analytics.component.html',
  styleUrls: ['./customer-analytics.component.css', './customer-analytics-kpi.css'] // KPI-CSS hinzugefügt
})
export class CustomerAnalyticsComponent implements OnChanges {
  @Input() customers: Customer[] = [];
  @Input() orders: Order[] = [];

  // KPI-Werte
  kpiTotalCustomers: number = 0;
  kpiNewCustomers30d: number = 0;
  kpiAvgRevenuePerCustomer: number = 0;

  // Chart-Optionen
  registrationsChartOptions: EChartsOption = {};
  topCustomersChartOptions: EChartsOption = {};
  topRevenueChartOptions: EChartsOption = {};

  constructor() {}

  ngOnChanges() {
    if (this.customers && this.orders) {
      this.prepareCharts();
    }
  }

  // Helper zum Gruppieren
  private groupBy<T>(array: T[], keyExtractor: (item: T) => string | null): Record<string, T[]> {
    return array.reduce((result, item) => {
      let key = keyExtractor(item);
      if (!key || key.trim().length === 0) {
        key = 'Unbekannt';
      }
      if (!result[key]) result[key] = [];
      result[key].push(item);
      return result;
    }, {} as Record<string, T[]>);
  }

  private prepareCharts() {
    if (!this.customers || this.customers.length === 0) return;

    // ------------------------------------------
    // 1. KPIs berechnen
    // ------------------------------------------
    this.kpiTotalCustomers = this.customers.length;

    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
    this.kpiNewCustomers30d = this.customers.filter(c => c.createdAt && new Date(c.createdAt) > thirtyDaysAgo).length;

    const totalRevenue = this.orders.reduce((sum, order) => sum + (order.totalAmount || 0), 0);
    this.kpiAvgRevenuePerCustomer = totalRevenue / this.kpiTotalCustomers;

    // ------------------------------------------
    // 2. Registrierungen über die Zeit (FIXED: Gruppiert nach MONAT)
    // ------------------------------------------
    const registrationGroups = this.groupBy(
      this.customers,
      c => c.createdAt ? c.createdAt.substring(0, 7) : null // z.B. "2025-11"
    );

    const dates = Object.keys(registrationGroups).sort();
    const dateCounts = dates.map(d => registrationGroups[d].length);

    this.registrationsChartOptions = {
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: dates },
      yAxis: { type: 'value' },
      series: [{
        data: dateCounts,
        type: 'line',
        smooth: true,
        itemStyle: { color: '#00c8ff' },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [{ offset: 0, color: 'rgba(0, 200, 255, 0.5)' }, { offset: 1, color: 'rgba(0, 200, 255, 0)' }]
          }
        }
      }]
    };

    // ------------------------------------------
    // 3. Top 10 Kunden nach Bestellanzahl (FIXED: Echte Namen)
    // ------------------------------------------
    const orderCountMap: Record<number, number> = {};
    this.orders.forEach(order => {
      const customerId = order.customer?.id;
      if (!customerId) return;
      orderCountMap[customerId] = (orderCountMap[customerId] || 0) + 1;
    });

    const customerOrderCounts = Object.entries(orderCountMap)
      .map(([id, count]) => {
        const customer = this.customers.find(c => c.id === Number(id));
        return {
          id: Number(id),
          name: customer ? `${customer.firstName} ${customer.lastName}` : `Kunde #${id}`,
          count: count
        };
      })
      .sort((a, b) => b.count - a.count)
      .slice(0, 10)
      .reverse(); // Für horizontales Balkendiagramm umdrehen

    this.topCustomersChartOptions = {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      xAxis: { type: 'value' },
      yAxis: {
        type: 'category',
        data: customerOrderCounts.map(c => c.name),
        axisLabel: { interval: 0 }
      },
      series: [{
        type: 'bar',
        data: customerOrderCounts.map(c => c.count),
        itemStyle: { color: '#00aaff' }
      }],
      grid: { left: 120, right: 20, top: 10, bottom: 20 } // Platz für Namen
    };

    // ------------------------------------------
    // 4. Top 10 Kunden nach Umsatz (NEU)
    // ------------------------------------------
    const revenueMap: Record<number, number> = {};
    this.orders.forEach(order => {
      const customerId = order.customer?.id;
      if (!customerId) return;
      revenueMap[customerId] = (revenueMap[customerId] || 0) + (order.totalAmount || 0);
    });

    const revenueData = Object.entries(revenueMap)
      .map(([id, revenue]) => {
        const customer = this.customers.find(c => c.id === Number(id));
        return {
          id: Number(id),
          name: customer ? `${customer.firstName} ${customer.lastName}` : `Kunde #${id}`,
          revenue: revenue
        };
      })
      .sort((a, b) => b.revenue - a.revenue)
      .slice(0, 10)
      .reverse(); // Für horizontales Balkendiagramm umdrehen

    this.topRevenueChartOptions = {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: (params: any) => `${params[0].name}: ${params[0].value.toFixed(2)} €` },
      xAxis: { type: 'value' },
      yAxis: {
        type: 'category',
        data: revenueData.map(r => r.name),
        axisLabel: { interval: 0 }
      },
      series: [{
        type: 'bar',
        data: revenueData.map(r => r.revenue),
        itemStyle: { color: '#00ffcc' }
      }],
      grid: { left: 120, right: 20, top: 10, bottom: 20 } // Platz für Namen
    };
  }
}