// order-management-frontend/src/app/inventory/inventory.component.ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgxEchartsModule } from 'ngx-echarts';
import { 
  InventoryService, 
  Warehouse, 
  WarehouseStock, 
  StockMovement,
  StockMovementRequest,
  MovementType,
  InventoryAiReport,
  DemandForecast,
  ReorderRecommendation,
  InventoryAnomaly,
  InventoryHealthScore
} from '../services/inventory.service';

@Component({
  selector: 'app-inventory',
  standalone: true,
  imports: [CommonModule, FormsModule, NgxEchartsModule],
  templateUrl: './inventory.component.html',
  styleUrls: ['./inventory.component.css']
})
export class InventoryComponent implements OnInit {
  
  activeTab: 'overview' | 'warehouses' | 'movements' | 'alerts' | 'ai' = 'overview';
  
  // Data
  warehouses: Warehouse[] = [];
  selectedWarehouse: Warehouse | null = null;
  warehouseStock: WarehouseStock[] = [];
  lowStockItems: WarehouseStock[] = [];
  movements: StockMovement[] = [];
  
  // Loading states
  loading = {
    warehouses: false,
    stock: false,
    movements: false,
    alerts: false
  };

  // Modals
  showWarehouseModal = false;
  showMovementModal = false;
  editingWarehouse: Partial<Warehouse> = {};
  
  // Movement Form
  movementForm: StockMovementRequest = {
    movementType: 'GOODS_RECEIPT',
    productId: 0,
    quantity: 0
  };

  // Products for dropdown
  products: { id: number; name: string }[] = [];
  
  // Chart
  stockChartOptions: any = {};
  movementChartOptions: any = {};

  // AI Data
  aiReport: InventoryAiReport | null = null;
  demandForecasts: DemandForecast[] = [];
  reorderRecommendations: ReorderRecommendation[] = [];
  inventoryAnomalies: InventoryAnomaly[] = [];
  healthScore: InventoryHealthScore | null = null;
  aiLoading = false;
  aiActiveSubTab: 'report' | 'forecasts' | 'reorder' | 'anomalies' = 'report';

  // AI Charts
  forecastChartOptions: any = {};
  healthScoreChartOptions: any = {};

  movementTypes: { value: MovementType; label: string }[] = [
    { value: 'GOODS_RECEIPT', label: 'Wareneingang' },
    { value: 'GOODS_ISSUE', label: 'Warenausgang' },
    { value: 'TRANSFER', label: 'Umbuchung' },
    { value: 'INVENTORY_ADJUSTMENT', label: 'Inventurkorrektur' },
    { value: 'RETURN', label: 'Retoure' },
    { value: 'SCRAP', label: 'Verschrottung' }
  ];

  constructor(private inventoryService: InventoryService) {}

  ngOnInit(): void {
    this.loadWarehouses();
    this.loadAlerts();
    this.loadProducts();
  }

  setActiveTab(tab: 'overview' | 'warehouses' | 'movements' | 'alerts' | 'ai'): void {
    this.activeTab = tab;
    
    if (tab === 'movements' && this.movements.length === 0) {
      this.loadMovements();
    }
    
    if (tab === 'ai' && !this.aiReport) {
      this.loadAiReport();
    }
  }

  // === Data Loading ===

  loadWarehouses(): void {
    this.loading.warehouses = true;
    this.inventoryService.getWarehouses().subscribe({
      next: (data) => {
        this.warehouses = data;
        if (data.length > 0 && !this.selectedWarehouse) {
          this.selectWarehouse(data[0]);
        }
        this.loading.warehouses = false;
        this.buildStockChart();
      },
      error: (err) => {
        console.error('Error loading warehouses:', err);
        this.loading.warehouses = false;
      }
    });
  }

  loadProducts(): void {
    // Produkte über den Stock-Endpoint laden
    this.inventoryService.getStockOverview().subscribe({
      next: (data) => {
        this.products = data.map(row => ({
          id: Number(row[0]),
          name: String(row[1])
        }));
      },
      error: (err) => console.error('Error loading products:', err)
    });
  }

  selectWarehouse(warehouse: Warehouse): void {
    this.selectedWarehouse = warehouse;
    this.loadWarehouseStock(warehouse.id);
  }

  loadWarehouseStock(warehouseId: number): void {
    this.loading.stock = true;
    this.inventoryService.getStockByWarehouse(warehouseId).subscribe({
      next: (data) => {
        this.warehouseStock = data;
        this.loading.stock = false;
      },
      error: (err) => {
        console.error('Error loading stock:', err);
        this.loading.stock = false;
      }
    });
  }

  loadAlerts(): void {
    this.loading.alerts = true;
    this.inventoryService.getLowStockItems().subscribe({
      next: (data) => {
        this.lowStockItems = data;
        this.loading.alerts = false;
      },
      error: (err) => {
        console.error('Error loading alerts:', err);
        this.loading.alerts = false;
      }
    });
  }

  loadMovements(): void {
    this.loading.movements = true;
    this.inventoryService.getMovements(0, 50).subscribe({
      next: (response) => {
        this.movements = response.content;
        this.loading.movements = false;
        this.buildMovementChart();
      },
      error: (err) => {
        console.error('Error loading movements:', err);
        this.loading.movements = false;
      }
    });
  }

  // === Warehouse CRUD ===

  openWarehouseModal(warehouse?: Warehouse): void {
    if (warehouse) {
      this.editingWarehouse = { ...warehouse };
    } else {
      this.editingWarehouse = {
        code: '',
        name: '',
        active: true,
        isDefault: false
      };
    }
    this.showWarehouseModal = true;
  }

  closeWarehouseModal(): void {
    this.showWarehouseModal = false;
    this.editingWarehouse = {};
  }

  saveWarehouse(): void {
    if (this.editingWarehouse.id) {
      this.inventoryService.updateWarehouse(this.editingWarehouse.id, this.editingWarehouse).subscribe({
        next: () => {
          this.loadWarehouses();
          this.closeWarehouseModal();
        },
        error: (err) => console.error('Error updating warehouse:', err)
      });
    } else {
      this.inventoryService.createWarehouse(this.editingWarehouse).subscribe({
        next: () => {
          this.loadWarehouses();
          this.closeWarehouseModal();
        },
        error: (err) => console.error('Error creating warehouse:', err)
      });
    }
  }

  // === Stock Movement ===

  openMovementModal(): void {
    this.movementForm = {
      movementType: 'GOODS_RECEIPT',
      productId: this.products.length > 0 ? this.products[0].id : 0,
      targetWarehouseId: this.selectedWarehouse?.id,
      quantity: 1
    };
    this.showMovementModal = true;
  }

  closeMovementModal(): void {
    this.showMovementModal = false;
  }

  onMovementTypeChange(): void {
    // Quell- und Ziellager je nach Bewegungstyp setzen
    if (this.movementForm.movementType === 'GOODS_ISSUE' || 
        this.movementForm.movementType === 'SCRAP') {
      this.movementForm.sourceWarehouseId = this.selectedWarehouse?.id;
      this.movementForm.targetWarehouseId = undefined;
    } else if (this.movementForm.movementType === 'TRANSFER') {
      this.movementForm.sourceWarehouseId = this.selectedWarehouse?.id;
    } else {
      this.movementForm.targetWarehouseId = this.selectedWarehouse?.id;
      this.movementForm.sourceWarehouseId = undefined;
    }
  }

  saveMovement(): void {
    this.inventoryService.createMovement(this.movementForm).subscribe({
      next: () => {
        this.closeMovementModal();
        this.loadWarehouseStock(this.selectedWarehouse!.id);
        this.loadMovements();
        this.loadAlerts();
      },
      error: (err) => {
        console.error('Error creating movement:', err);
        alert('Fehler: ' + (err.error?.message || 'Bewegung konnte nicht erstellt werden'));
      }
    });
  }

  // === Charts ===

  private buildStockChart(): void {
    if (this.warehouses.length === 0) return;

    this.stockChartOptions = {
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: this.warehouses.map(w => w.name)
      },
      yAxis: [
        { type: 'value', name: 'Bestand' },
        { 
          type: 'value', 
          name: 'Wert (€)',
          axisLabel: { formatter: (v: number) => `${(v / 1000).toFixed(0)}k` }
        }
      ],
      series: [
        {
          name: 'Gesamtbestand',
          type: 'bar',
          data: this.warehouses.map(w => w.totalStock || 0),
          itemStyle: { color: '#3b82f6' }
        },
        {
          name: 'Lagerwert',
          type: 'line',
          yAxisIndex: 1,
          data: this.warehouses.map(w => w.inventoryValue || 0),
          smooth: true,
          itemStyle: { color: '#10b981' }
        }
      ]
    };
  }

  private buildMovementChart(): void {
    // Bewegungen nach Typ gruppieren
    const typeCounts: Record<string, number> = {};
    this.movements.forEach(m => {
      const label = this.inventoryService.getMovementTypeLabel(m.movementType);
      typeCounts[label] = (typeCounts[label] || 0) + 1;
    });

    this.movementChartOptions = {
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        data: Object.entries(typeCounts).map(([name, value]) => ({ name, value })),
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }]
    };
  }

  // === Helpers ===

  getStockStatusClass(status: string): string {
    return this.inventoryService.getStockStatusColor(status);
  }

  getStockStatusLabel(status: string): string {
    return this.inventoryService.getStockStatusLabel(status);
  }

  getMovementTypeLabel(type: MovementType): string {
    return this.inventoryService.getMovementTypeLabel(type);
  }

  formatCurrency(value: number | null | undefined): string {
    if (value == null) return '0,00 €';
    return new Intl.NumberFormat('de-DE', {
      style: 'currency',
      currency: 'EUR'
    }).format(value);
  }

  formatNumber(value: number | null | undefined): string {
    if (value == null) return '0';
    return new Intl.NumberFormat('de-DE').format(value);
  }

  formatDate(date: string): string {
    return new Date(date).toLocaleString('de-DE');
  }

  getTotalInventoryValue(): number {
    return this.warehouses.reduce((sum, w) => sum + (w.inventoryValue || 0), 0);
  }

  getTotalStock(): number {
    return this.warehouses.reduce((sum, w) => sum + (w.totalStock || 0), 0);
  }

  needsSourceWarehouse(): boolean {
    return ['GOODS_ISSUE', 'TRANSFER', 'SCRAP'].includes(this.movementForm.movementType);
  }

  needsTargetWarehouse(): boolean {
    return ['GOODS_RECEIPT', 'TRANSFER', 'RETURN', 'INVENTORY_ADJUSTMENT'].includes(this.movementForm.movementType);
  }

  // Handler für Alert-Button
  handleAlertAction(item: WarehouseStock): void {
    // Finde das passende Warehouse anhand der ID
    const warehouse = this.warehouses.find(w => w.id === item.warehouseId);
    if (warehouse) {
      this.selectWarehouse(warehouse);
    }
    this.openMovementModal();
  }

  // === AI Methods ===

  setAiSubTab(tab: 'report' | 'forecasts' | 'reorder' | 'anomalies'): void {
    this.aiActiveSubTab = tab;
  }

  loadAiReport(): void {
    this.aiLoading = true;
    this.inventoryService.getAiReport().subscribe({
      next: (report) => {
        this.aiReport = report;
        this.demandForecasts = report.demandForecasts || [];
        this.reorderRecommendations = report.reorderRecommendations || [];
        this.inventoryAnomalies = report.anomalies || [];
        this.healthScore = report.healthScore;
        this.aiLoading = false;
        this.buildAiCharts();
      },
      error: (err) => {
        console.error('Error loading AI report:', err);
        this.aiLoading = false;
      }
    });
  }

  refreshAiReport(): void {
    this.aiReport = null;
    this.loadAiReport();
  }

  private buildAiCharts(): void {
    this.buildForecastChart();
    this.buildHealthScoreChart();
  }

  private buildForecastChart(): void {
    if (!this.demandForecasts.length) return;

    const topForecasts = this.demandForecasts.slice(0, 8);

    this.forecastChartOptions = {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' }
      },
      legend: {
        data: ['Aktueller Bestand', '7 Tage', '14 Tage', '30 Tage'],
        textStyle: { color: '#94a3b8' }
      },
      xAxis: {
        type: 'category',
        data: topForecasts.map(f => f.productName.length > 15 
          ? f.productName.substring(0, 15) + '...' 
          : f.productName),
        axisLabel: { 
          rotate: 45,
          color: '#94a3b8'
        }
      },
      yAxis: {
        type: 'value',
        name: 'Einheiten',
        axisLabel: { color: '#94a3b8' }
      },
      series: [
        {
          name: 'Aktueller Bestand',
          type: 'bar',
          data: topForecasts.map(f => f.currentStock),
          itemStyle: { color: '#3b82f6' }
        },
        {
          name: '7 Tage',
          type: 'bar',
          data: topForecasts.map(f => f.predictedDemand7Days),
          itemStyle: { color: '#10b981' }
        },
        {
          name: '14 Tage',
          type: 'bar',
          data: topForecasts.map(f => f.predictedDemand14Days),
          itemStyle: { color: '#f59e0b' }
        },
        {
          name: '30 Tage',
          type: 'bar',
          data: topForecasts.map(f => f.predictedDemand30Days),
          itemStyle: { color: '#ef4444' }
        }
      ]
    };
  }

  private buildHealthScoreChart(): void {
    if (!this.healthScore) return;

    this.healthScoreChartOptions = {
      tooltip: {
        trigger: 'item',
        formatter: '{b}: {c}%'
      },
      series: [{
        type: 'gauge',
        startAngle: 180,
        endAngle: 0,
        min: 0,
        max: 100,
        splitNumber: 5,
        radius: '90%',
        center: ['50%', '70%'],
        axisLine: {
          lineStyle: {
            width: 20,
            color: [
              [0.3, '#dc3545'],
              [0.5, '#fd7e14'],
              [0.7, '#ffc107'],
              [0.9, '#7cb342'],
              [1, '#28a745']
            ]
          }
        },
        pointer: {
          itemStyle: { color: '#475569' },
          length: '60%',
          width: 8
        },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { 
          distance: 30,
          color: '#94a3b8',
          fontSize: 12
        },
        title: { show: false },
        detail: {
          valueAnimation: true,
          formatter: (value: number) => `${value}\n${this.healthScore?.grade || ''}`,
          fontSize: 24,
          color: this.inventoryService.getGradeColor(this.healthScore?.grade || 'C'),
          offsetCenter: [0, '0%']
        },
        data: [{ value: this.healthScore.overallScore }]
      }]
    };
  }

  // AI Helper Methods
  getUrgencyColor(urgency: string): string {
    return this.inventoryService.getUrgencyColor(urgency);
  }

  getTrendIcon(trend: string): string {
    return this.inventoryService.getTrendIcon(trend);
  }

  getSeverityColor(severity: string): string {
    return this.inventoryService.getSeverityColor(severity);
  }

  getGradeColor(grade: string): string {
    return this.inventoryService.getGradeColor(grade);
  }

  getConfidenceLabel(score: number): string {
    if (score >= 0.8) return 'Hoch';
    if (score >= 0.6) return 'Mittel';
    return 'Niedrig';
  }

  formatDateShort(dateStr: string): string {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleDateString('de-DE');
  }
}
