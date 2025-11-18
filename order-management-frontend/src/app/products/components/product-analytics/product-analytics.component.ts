// src/app/products/components/product-analytics/product-analytics.component.ts
import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgxEchartsModule } from 'ngx-echarts';
import { Product, ProductCategory } from '../../../types';

@Component({
  selector: 'app-product-analytics',
  standalone: true,
  imports: [CommonModule, NgxEchartsModule],
  templateUrl: './product-analytics.component.html',
  styleUrls: ['./product-analytics.component.css']
})
export class ProductAnalyticsComponent implements OnChanges {
  @Input() products: Product[] = [];
  @Input() selectedCategory: ProductCategory | '' = '';

  totalProducts = 0;
  activeProducts = 0;
  avgPrice = 0;
  totalStock = 0;

  chartCategoryCount: any;
  chartActiveInactive: any;
  chartAvgPriceCategory: any;

  ngOnChanges() {
    const data = this.selectedCategory
      ? this.products.filter(p => p.category === this.selectedCategory)
      : this.products;

    this.totalProducts = data.length;
    this.activeProducts = data.filter(p => p.active).length;
    this.totalStock = data.reduce((acc, p) => acc + p.stockQuantity, 0);
    this.avgPrice = data.length
      ? data.reduce((acc, p) => acc + p.price, 0) / data.length
      : 0;

    this.buildCharts(data);
  }

  buildCharts(data: Product[]) {
    const categoryData = Object.values(ProductCategory).map(cat => ({
      name: cat,
      count: data.filter(p => p.category === cat).length,
      avgPrice:
        data.filter(p => p.category === cat).reduce((a, p) => a + p.price, 0) /
        (data.filter(p => p.category === cat).length || 1)
    }));

    this.chartCategoryCount = {
      backgroundColor: 'transparent',
      xAxis: {
        type: 'category',
        data: categoryData.map(c => c.name),
        axisLabel: { color: '#89a6b8' }
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: '#89a6b8' }
      },
      series: [
        {
          data: categoryData.map(c => c.count),
          type: 'bar',
          itemStyle: { color: '#00e5ff' }
        }
      ]
    };

    this.chartActiveInactive = {
      backgroundColor: 'transparent',
      tooltip: { trigger: 'item' },
      series: [
        {
          type: 'pie',
          radius: '60%',
          data: [
            { value: data.filter(p => p.active).length, name: 'Aktiv' },
            { value: data.filter(p => !p.active).length, name: 'Inaktiv' }
          ],
          label: { color: '#e0f0ff' }
        }
      ]
    };

    this.chartAvgPriceCategory = {
      backgroundColor: 'transparent',
      xAxis: {
        type: 'category',
        data: categoryData.map(c => c.name),
        axisLabel: { color: '#89a6b8' }
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: '#89a6b8' }
      },
      series: [
        {
          data: categoryData.map(c => c.avgPrice),
          type: 'bar',
          itemStyle: { color: '#00d084' }
        }
      ]
    };
  }
}
