// src/app/products/components/product-form/product-form.component.ts
// src/app/products/components/product-form/product-form.component.ts
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CreateProductRequest, ProductCategory } from '../../../types';

@Component({
  selector: 'app-product-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './product-form.component.html',
  styleUrls: ['./product-form.component.css']
})
export class ProductFormComponent {
  @Input() product: CreateProductRequest | null = null;

  @Output() save = new EventEmitter<CreateProductRequest>();
  @Output() cancel = new EventEmitter<void>();

  ProductCategory = ProductCategory;

  get model(): CreateProductRequest {
    return (
      this.product ?? {
        name: '',
        description: '',
        price: 0,
        stockQuantity: 0,
        category: ProductCategory.ELEKTRONIK,
        imageUrl: '',
        active: true
      }
    );
  }

  triggerSave() {
    this.save.emit(this.model);
  }
}
