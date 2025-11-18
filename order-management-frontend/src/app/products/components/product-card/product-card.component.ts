// src/app/products/components/product-card/product-card.component.ts
import { Component, EventEmitter, Input, Output, OnInit } from '@angular/core'; // OnInit importieren
import { CommonModule } from '@angular/common';
import { Product } from '../../../types';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-product-card',
  standalone: true, // standalone: true hinzufügen
  imports: [CommonModule],
  templateUrl: './product-card.component.html',
  styleUrls: ['./product-card.component.css']
})
export class ProductCardComponent implements OnInit { // OnInit implementieren
  @Input() product!: Product;
  @Output() edit = new EventEmitter<Product>();
  @Output() delete = new EventEmitter<string | number>();

  // Eine neue Eigenschaft, um die URL zu speichern
  finalImageUrl: string = '';

  ngOnInit() {
    // Die URL nur EINMAL beim Initialisieren der Komponente erstellen
    this.finalImageUrl = this.buildImageUrl(this.product.imageUrl);
  }

  // Diese Funktion wird jetzt in buildImageUrl umbenannt und nur in ngOnInit aufgerufen
  buildImageUrl(imageUrl: string | undefined): string {
    const backendBase = environment.apiUrl.replace('/api', '');
    const timestamp = Date.now(); // Zeitstempel nur einmal holen

    if (!imageUrl) {
      return `${backendBase}/images/placeholder.jpg?t=${timestamp}`;
    }
    if (!imageUrl.startsWith('http')) {
      if (!imageUrl.startsWith('/')) {
        imageUrl = '/' + imageUrl;
      }
      if (!imageUrl.startsWith('/images/')) {
        imageUrl = '/images' + imageUrl;
      }
      return `${backendBase}${imageUrl}?t=${timestamp}`;
    }
    return imageUrl;
  }

  getStockStatusClass(stockQuantity: number): string {
    if (stockQuantity <= 5) return 'stock-low';
    if (stockQuantity <= 15) return 'stock-medium';
    return 'stock-good';
  }
}