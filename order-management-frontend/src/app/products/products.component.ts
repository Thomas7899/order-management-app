// src/app/products/products.component.ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProductService } from '../services/product.service';
import {
  Product,
  ProductCategory,
  CreateProductRequest,
  UpdateProductRequest,
  ID
} from '../types';
import { ProductCardComponent } from './components/product-card/product-card.component';
import { ProductFormComponent } from './components/product-form/product-form.component';
import { ProductAnalyticsComponent } from './components/product-analytics/product-analytics.component';

@Component({
  selector: 'app-products',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ProductCardComponent,
    ProductFormComponent,
    ProductAnalyticsComponent
  ],
  templateUrl: './products.component.html',
  styleUrls: ['./products.component.css']
})
export class ProductsComponent implements OnInit {
  products: Product[] = [];
  filteredProducts: Product[] = [];
  categories: ProductCategory[] = [];

  searchTerm = '';
  selectedCategory: '' | ProductCategory = '';
  showOnlyActive = false;

  showForm = false;
  editingProduct: Product | null = null;

  constructor(private productService: ProductService) {}

  ngOnInit(): void {
    this.loadProducts();
    this.loadCategories();
  }

  loadProducts(): void {
    this.productService.getAll().subscribe(products => {
      this.products = products;
      this.applyFilters();
    });
  }

  loadCategories(): void {
    this.productService.getCategories().subscribe(cats => {
      this.categories = cats;
    });
  }

  applyFilters(): void {
    this.filteredProducts = this.products.filter(product => {
      const matchesSearch =
        !this.searchTerm ||
        product.name.toLowerCase().includes(this.searchTerm.toLowerCase());

      const matchesCategory =
        !this.selectedCategory || product.category === this.selectedCategory;

      const matchesActive = !this.showOnlyActive || product.active;

      return matchesSearch && matchesCategory && matchesActive;
    });
  }

  toggleAddForm(): void {
    this.showForm = !this.showForm;
    if (!this.showForm) {
      this.editingProduct = null;
    }
  }

  saveProduct(data: CreateProductRequest): void {
    if (this.editingProduct) {
      const update: UpdateProductRequest = {
        ...data,
        id: this.editingProduct.id
      };

      this.productService.update(this.editingProduct.id as ID, update).subscribe(() => {
        this.loadProducts();
        this.showForm = false;
        this.editingProduct = null;
      });
    } else {
      this.productService.create(data).subscribe(() => {
        this.loadProducts();
        this.showForm = false;
      });
    }
  }

  editProduct(product: Product): void {
    this.editingProduct = product;
    this.showForm = true;
  }

  deleteProduct(id: ID): void {
    this.productService.delete(id).subscribe(() => {
      this.loadProducts();
    });
  }
}
