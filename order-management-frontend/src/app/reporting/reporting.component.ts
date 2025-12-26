// order-management-frontend/src/app/reporting/reporting.component.ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgxEchartsModule } from 'ngx-echarts';
import { 
  ReportingService, 
  AbcAnalysis, 
  SalesForecast, 
  KpiDashboard 
} from '../services/reporting.service';

@Component({
  selector: 'app-reporting',
  standalone: true,
  imports: [CommonModule, FormsModule, NgxEchartsModule],
  templateUrl: './reporting.component.html',
  styleUrls: ['./reporting.component.css']
})
export class ReportingComponent implements OnInit {
  
  activeTab: 'kpi' | 'abc' | 'forecast' = 'kpi';
  abcType: 'products' | 'customers' = 'products';
  
  // Data
  kpiData: KpiDashboard | null = null;
  productAbcData: AbcAnalysis | null = null;
  customerAbcData: AbcAnalysis | null = null;
  forecastData: SalesForecast | null = null;
  
  // Loading states
  loading = {
    kpi: false,
    abc: false,
    forecast: false
  };
  
  // Chart Options
  abcChartOptions: any = {};
  forecastChartOptions: any = {};
  revenueTrendChartOptions: any = {};
  orderTrendChartOptions: any = {};
  orderStatusChartOptions: any = {};
  inventoryCategoryChartOptions: any = {};

  constructor(private reportingService: ReportingService) {}

  ngOnInit(): void {
    this.loadKpiData();
    this.loadAbcData();
    this.loadForecastData();
  }

  setActiveTab(tab: 'kpi' | 'abc' | 'forecast'): void {
    this.activeTab = tab;
  }

  setAbcType(type: 'products' | 'customers'): void {
    this.abcType = type;
    this.buildAbcChart();
  }

  loadKpiData(): void {
    this.loading.kpi = true;
    this.reportingService.getKpiDashboard().subscribe({
      next: (data) => {
        this.kpiData = data;
        this.buildKpiCharts();
        this.loading.kpi = false;
      },
      error: (err) => {
        console.error('Error loading KPI data:', err);
        this.loading.kpi = false;
      }
    });
  }

  loadAbcData(): void {
    this.loading.abc = true;
    
    // Lade beide ABC-Analysen parallel
    this.reportingService.getProductAbcAnalysis().subscribe({
      next: (data) => {
        this.productAbcData = data;
        if (this.abcType === 'products') {
          this.buildAbcChart();
        }
      },
      error: (err) => console.error('Error loading product ABC:', err)
    });

    this.reportingService.getCustomerAbcAnalysis().subscribe({
      next: (data) => {
        this.customerAbcData = data;
        if (this.abcType === 'customers') {
          this.buildAbcChart();
        }
        this.loading.abc = false;
      },
      error: (err) => {
        console.error('Error loading customer ABC:', err);
        this.loading.abc = false;
      }
    });
  }

  loadForecastData(): void {
    this.loading.forecast = true;
    this.reportingService.getSalesForecast(12, 3).subscribe({
      next: (data) => {
        this.forecastData = data;
        this.buildForecastChart();
        this.loading.forecast = false;
      },
      error: (err) => {
        console.error('Error loading forecast:', err);
        this.loading.forecast = false;
      }
    });
  }

  private buildKpiCharts(): void {
    if (!this.kpiData) return;

    // Revenue Trend Chart
    this.revenueTrendChartOptions = {
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: this.kpiData.revenueTrend.map(t => t.period)
      },
      yAxis: {
        type: 'value',
        axisLabel: {
          formatter: (value: number) => `${(value / 1000).toFixed(0)}k€`
        }
      },
      series: [{
        name: 'Umsatz',
        type: 'line',
        data: this.kpiData.revenueTrend.map(t => t.value),
        smooth: true,
        areaStyle: { opacity: 0.3 },
        lineStyle: { width: 3 },
        itemStyle: { color: '#3b82f6' }
      }]
    };

    // Order Trend Chart
    this.orderTrendChartOptions = {
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: this.kpiData.orderTrend.map(t => t.period)
      },
      yAxis: { type: 'value' },
      series: [{
        name: 'Bestellungen',
        type: 'bar',
        data: this.kpiData.orderTrend.map(t => t.value),
        itemStyle: { color: '#10b981' }
      }]
    };

    // Order Status Pie Chart
    const operational = this.kpiData.operational;
    this.orderStatusChartOptions = {
      tooltip: { trigger: 'item' },
      legend: { orient: 'vertical', right: 10, top: 'center' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        label: { show: false },
        data: [
          { value: operational.pendingOrders, name: 'Ausstehend', itemStyle: { color: '#f59e0b' } },
          { value: operational.processingOrders, name: 'In Bearbeitung', itemStyle: { color: '#8b5cf6' } },
          { value: operational.shippedOrders, name: 'Versandt', itemStyle: { color: '#06b6d4' } },
          { value: operational.deliveredOrders, name: 'Geliefert', itemStyle: { color: '#10b981' } },
          { value: operational.cancelledOrders, name: 'Storniert', itemStyle: { color: '#ef4444' } }
        ]
      }]
    };

    // Inventory by Category
    const inventory = this.kpiData.inventory;
    const categories = Object.entries(inventory.inventoryByCategory);
    this.inventoryCategoryChartOptions = {
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: categories.map(([cat]) => cat),
        axisLabel: { rotate: 45 }
      },
      yAxis: {
        type: 'value',
        axisLabel: {
          formatter: (value: number) => `${(value / 1000).toFixed(0)}k€`
        }
      },
      series: [{
        type: 'bar',
        data: categories.map(([_, value]) => value),
        itemStyle: { color: '#6366f1' }
      }]
    };
  }

  private buildAbcChart(): void {
    const data = this.abcType === 'products' ? this.productAbcData : this.customerAbcData;
    if (!data) return;

    const topItems = data.items.slice(0, 20);

    this.abcChartOptions = {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params: any) => {
          const item = params[0];
          return `${item.name}<br/>Umsatz: ${item.value.toLocaleString('de-DE')}€<br/>Klasse: ${topItems[item.dataIndex]?.abcClass}`;
        }
      },
      xAxis: {
        type: 'category',
        data: topItems.map(item => item.name.substring(0, 15)),
        axisLabel: { rotate: 45, fontSize: 10 }
      },
      yAxis: [
        {
          type: 'value',
          name: 'Umsatz (€)',
          axisLabel: { formatter: (v: number) => `${(v / 1000).toFixed(0)}k` }
        },
        {
          type: 'value',
          name: 'Kumuliert (%)',
          max: 100,
          axisLabel: { formatter: '{value}%' }
        }
      ],
      series: [
        {
          name: 'Umsatz',
          type: 'bar',
          data: topItems.map(item => ({
            value: item.revenue,
            itemStyle: {
              color: item.abcClass === 'A' ? '#10b981' 
                   : item.abcClass === 'B' ? '#f59e0b' 
                   : '#ef4444'
            }
          }))
        },
        {
          name: 'Kumuliert',
          type: 'line',
          yAxisIndex: 1,
          data: topItems.map(item => item.cumulativePercentage),
          smooth: true,
          lineStyle: { color: '#3b82f6', width: 2 }
        }
      ]
    };
  }

  private buildForecastChart(): void {
    if (!this.forecastData) return;

    const allData = [
      ...this.forecastData.historicalData,
      ...this.forecastData.forecastData
    ];

    this.forecastChartOptions = {
      tooltip: { trigger: 'axis' },
      legend: { data: ['Ist-Umsatz', 'Prognose'] },
      xAxis: {
        type: 'category',
        data: allData.map(d => d.period),
        axisLabel: { rotate: 45 }
      },
      yAxis: {
        type: 'value',
        axisLabel: {
          formatter: (value: number) => `${(value / 1000).toFixed(0)}k€`
        }
      },
      series: [
        {
          name: 'Ist-Umsatz',
          type: 'bar',
          data: allData.map(d => d.actualRevenue),
          itemStyle: { color: '#3b82f6' }
        },
        {
          name: 'Prognose',
          type: 'line',
          data: allData.map(d => d.forecastedRevenue || d.actualRevenue),
          smooth: true,
          lineStyle: { 
            color: '#10b981', 
            type: 'dashed',
            width: 2
          },
          itemStyle: { color: '#10b981' }
        }
      ]
    };
  }

  formatCurrency(value: number | null | undefined): string {
    if (value == null) return '0,00 €';
    return new Intl.NumberFormat('de-DE', {
      style: 'currency',
      currency: 'EUR'
    }).format(value);
  }

  formatPercent(value: number | null | undefined): string {
    if (value == null) return '0%';
    return `${value >= 0 ? '+' : ''}${value.toFixed(1)}%`;
  }

  formatNumber(value: number | null | undefined): string {
    if (value == null) return '0';
    return new Intl.NumberFormat('de-DE').format(value);
  }

  getTrendIcon(value: number | null | undefined): string {
    if (value == null || value === 0) return '➡️';
    return value > 0 ? '📈' : '📉';
  }

  getTrendClass(value: number | null | undefined): string {
    if (value == null || value === 0) return 'neutral';
    return value > 0 ? 'positive' : 'negative';
  }

  getAbcClassColor(abcClass: string): string {
    switch (abcClass) {
      case 'A': return '#10b981';
      case 'B': return '#f59e0b';
      case 'C': return '#ef4444';
      default: return '#6b7280';
    }
  }
}
