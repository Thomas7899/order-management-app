// src/app/products/products.component.ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProductService } from '../services/product.service';
import { Product, ProductCategory, CreateProductRequest, UpdateProductRequest, ProductFilter } from '../types/index';
import { environment } from '../../environments/environment';
import { ProductCardComponent } from './components/product-card/product-card.component';
import { ProductFormComponent } from './components/product-form/product-form.component';
import { ProductFiltersComponent } from './components/product-filters/product-filters.component';

@Component({
  selector: 'app-products',
  standalone: true,
  imports: [CommonModule, FormsModule, ProductCardComponent, ProductFormComponent, ProductFiltersComponent],
  templateUrl: './products.component.html',
  styleUrls: ['./products.component.css']
})
export class ProductsComponent implements OnInit {

  products: Product[] = [];
  filteredProducts: Product[] = [];
  showForm = false;
  editingProduct: Product | null = null;
  searchTerm = '';
  selectedCategory = '';
  showOnlyActive = false;
  snackbarMessage: string | null = null;

  newProduct: CreateProductRequest = {
    name: '',
    category: ProductCategory.ELEKTRONIK,
    description: '',
    price: 0,
    stockQuantity: 0,
    imageUrl: '',
    active: true
  };

  constructor(private productService: ProductService) {}

  ngOnInit(): void {
    this.loadProducts();
  }

  loadProducts(): void {
    this.productService.getAll().subscribe({
      next: products => {
        this.products = products;
        this.filteredProducts = products;
        this.applyFilters();
      },
      error: error => console.error('Fehler beim Laden der Produkte:', error)
    });
  }

  applyFilters(): void {
    const filter: ProductFilter = {
      search: this.searchTerm.trim() || undefined,
      category: this.selectedCategory as ProductCategory || undefined,
      active: this.showOnlyActive || undefined
    };

    if (this.hasActiveFilters(filter)) {
      this.productService.getWithFilter(filter).subscribe({
        next: products => this.filteredProducts = products,
        error: () => this.filteredProducts = this.filterProductsLocally()
      });
    } else {
      this.filteredProducts = [...this.products];
    }
  }

  private hasActiveFilters(filter: ProductFilter): boolean {
    return !!(filter.search || filter.category || filter.active);
  }

  private filterProductsLocally(): Product[] {
    return this.products.filter(p => {
      const matchesSearch =
        !this.searchTerm ||
        p.name.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        p.description?.toLowerCase().includes(this.searchTerm.toLowerCase());
      const matchesCategory = !this.selectedCategory || p.category === this.selectedCategory;
      const matchesActive = !this.showOnlyActive || p.active;
      return matchesSearch && matchesCategory && matchesActive;
    });
  }

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

  addProduct(): void {
    this.editingProduct ? this.updateProduct() : this.createProduct();
  }

  private createProduct(): void {
    this.productService.create(this.newProduct).subscribe({
      next: product => {
        this.products.push(product);
        this.applyFilters();
        this.resetForm();
        this.showSnackbar('Produkt gespeichert ✔️');
      },
      error: error => console.error('Fehler beim Erstellen des Produkts:', error)
    });
  }

  private updateProduct(): void {
    if (!this.editingProduct?.id) return;
    const updateRequest: UpdateProductRequest = { ...this.newProduct, id: this.editingProduct.id };
    const productId = Number(this.editingProduct.id);

    this.productService.update(productId, updateRequest).subscribe({
      next: updatedProduct => {
        const index = this.products.findIndex(p => p.id === updatedProduct.id);
        if (index > -1) this.products[index] = updatedProduct;
        this.applyFilters();
        this.resetForm();
        this.showSnackbar('Produkt aktualisiert ✔️');
      },
      error: error => console.error('Fehler beim Aktualisieren:', error)
    });
  }

  editProduct(product: Product): void {
    this.editingProduct = product;
    this.newProduct = { ...product };
    this.showForm = true;
  }

  deleteProduct(id: string | number): void {
    if (!confirm('Produkt löschen?')) return;
    const productId = Number(id);
    this.productService.delete(productId).subscribe({
      next: () => {
        this.products = this.products.filter(p => p.id !== productId);
        this.applyFilters();
        this.showSnackbar('Produkt gelöscht 🗑️');
      },
      error: error => console.error('Fehler beim Löschen:', error)
    });
  }

  resetForm(): void {
    this.newProduct = {
      name: '',
      category: ProductCategory.ELEKTRONIK,
      description: '',
      price: 0,
      stockQuantity: 0,
      imageUrl: '',
      active: true
    };
    this.editingProduct = null;
    this.showForm = false;
  }

  showSnackbar(message: string) {
    this.snackbarMessage = message;
    setTimeout(() => this.snackbarMessage = null, 3000);
  }
}
