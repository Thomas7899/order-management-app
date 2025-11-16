// src/app/products/components/product-card/product-card.component.ts
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Product } from '../../../types';
import { environment } from '../../../../environments/environment';

@Component({
    selector: 'app-product-card',
    imports: [CommonModule],
    templateUrl: './product-card.component.html',
    styleUrls: ['./product-card.component.css']
})
export class ProductCardComponent {
  @Input() product!: Product;
  @Output() edit = new EventEmitter<Product>();
  @Output() delete = new EventEmitter<string | number>();

  getImageUrl(imageUrl: string | undefined): string {
    const backendBase = environment.apiUrl.replace('/api', '');
    if (!imageUrl) {
      return `${backendBase}/images/placeholder.jpg?t=${Date.now()}`;
    }
    if (!imageUrl.startsWith('http')) {
      if (!imageUrl.startsWith('/')) {
        imageUrl = '/' + imageUrl;
      }
      if (!imageUrl.startsWith('/images/')) {
        imageUrl = '/images' + imageUrl;
      }
      return `${backendBase}${imageUrl}?t=${Date.now()}`;
    }
    return imageUrl;
  }

  getStockStatusClass(stockQuantity: number): string {
    if (stockQuantity <= 5) return 'stock-low';
    if (stockQuantity <= 15) return 'stock-medium';
    return 'stock-good';
  }
}
