import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProductService } from '../services/product.service';
import { Product, ProductCategory, CreateProductRequest, UpdateProductRequest, ProductFilter } from '../types/index';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-products',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container">
      <h2>Produktverwaltung</h2>
      
      <!-- Filter Section -->
      <div class="filters-section">
        <div class="filter-row">
          <input type="text" [(ngModel)]="searchTerm" (input)="applyFilters()" 
                 placeholder="Produkte suchen..." class="form-control search-input">
          
          <select [(ngModel)]="selectedCategory" (change)="applyFilters()" class="form-control">
            <option value="">Alle Kategorien</option>
            <option value="Elektronik">Elektronik</option>
            <option value="Möbel">Möbel</option>
            <option value="Beleuchtung">Beleuchtung</option>
            <option value="Bürobedarf">Bürobedarf</option>
          </select>
          
          <label class="checkbox-label">
            <input type="checkbox" [(ngModel)]="showOnlyActive" (change)="applyFilters()">
            Nur aktive Produkte
          </label>
        </div>
      </div>
      
      <button (click)="showForm = !showForm" class="btn btn-primary mb-3">
        {{ showForm ? 'Abbrechen' : 'Neues Produkt hinzufügen' }}
      </button>
      
      <div *ngIf="showForm" class="card form-card">
        <div class="card-body">
          <h5>Produkt hinzufügen</h5>
          <form (ngSubmit)="addProduct()" #form="ngForm">
            <div class="form-row">
              <div class="mb-3">
                <label>Name:</label>
                <input type="text" [(ngModel)]="newProduct.name" name="name" required class="form-control">
              </div>
              <div class="mb-3">
                <label>Preis:</label>
                <input type="number" [(ngModel)]="newProduct.price" name="price" required class="form-control">
              </div>
            </div>
            <div class="form-row">
              <div class="mb-3">
                <label>Kategorie:</label>
                <select [(ngModel)]="newProduct.category" name="category" required class="form-control">
                  <option value="Elektronik">Elektronik</option>
                  <option value="Möbel">Möbel</option>
                  <option value="Beleuchtung">Beleuchtung</option>
                  <option value="Bürobedarf">Bürobedarf</option>
                </select>
              </div>
              <div class="mb-3">
                <label>Lagerbestand:</label>
                <input type="number" [(ngModel)]="newProduct.stockQuantity" name="stockQuantity" required class="form-control">
              </div>
            </div>
            <div class="mb-3">
              <label>Beschreibung:</label>
              <textarea [(ngModel)]="newProduct.description" name="description" class="form-control" rows="3"></textarea>
            </div>
            <div class="form-actions">
              <button type="submit" class="btn btn-success">Speichern</button>
              <button type="button" (click)="showForm = false" class="btn btn-secondary">Abbrechen</button>
            </div>
          </form>
        </div>
      </div>
      
      <div class="products-grid">
        <div *ngFor="let product of filteredProducts" class="product-card">
          <div class="product-image">
            <img [src]="getImageUrl(product.imageUrl)" [alt]="product.name" class="product-img">
          </div>
          <div class="product-info">
            <h5 class="product-title">{{ product.name }}</h5>
            <p class="product-description">{{ product.description }}</p>
            <div class="product-details">
              <span class="product-price">{{ product.price | currency:'EUR':'symbol':'1.2-2' }}</span>
              <span class="product-category">{{ product.category }}</span>
            </div>
            <div class="product-stock">
              <span [class]="getStockStatusClass(product.stockQuantity)">
                Lager: {{ product.stockQuantity }}
              </span>
            </div>
            <div class="product-actions">
              <button (click)="editProduct(product)" class="btn btn-sm btn-outline-primary">Bearbeiten</button>
              <button (click)="deleteProduct(product.id)" class="btn btn-sm btn-outline-danger">Löschen</button>
            </div>
          </div>
        </div>
      </div>
      
      <div *ngIf="filteredProducts.length === 0" class="no-products">
        <p>Keine Produkte gefunden.</p>
      </div>
    </div>
  `,
  styles: [`
    .container { padding: 20px; max-width: 1200px; margin: 0 auto; }
    
    .filters-section { 
      background: #f8f9fa; 
      padding: 15px; 
      border-radius: 8px; 
      margin-bottom: 20px; 
    }
    
    .filter-row { 
      display: grid; 
      grid-template-columns: 1fr auto auto; 
      gap: 15px; 
      align-items: center; 
    }
    
    .search-input { 
      max-width: 300px; 
    }
    
    .checkbox-label { 
      display: flex; 
      align-items: center; 
      gap: 5px; 
      margin: 0; 
      white-space: nowrap; 
    }
    
    .form-card { 
      margin: 20px 0; 
      box-shadow: 0 2px 10px rgba(0,0,0,0.1); 
    }
    
    .form-row { 
      display: grid; 
      grid-template-columns: 1fr 1fr; 
      gap: 15px; 
    }
    
    .form-actions { 
      display: flex; 
      gap: 10px; 
      margin-top: 20px; 
    }
    
    .products-grid { 
      display: grid; 
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); 
      gap: 20px; 
      margin-top: 20px; 
    }
    
    .product-card { 
      background: white; 
      border-radius: 8px; 
      box-shadow: 0 2px 8px rgba(0,0,0,0.1); 
      overflow: hidden; 
      transition: transform 0.2s, box-shadow 0.2s; 
    }
    
    .product-card:hover { 
      transform: translateY(-2px); 
      box-shadow: 0 4px 15px rgba(0,0,0,0.15); 
    }
    
    .product-image { 
      height: 200px; 
      overflow: hidden; 
      background: #f8f9fa; 
      display: flex;
      align-items: center;
      justify-content: center;
    }
    
    .product-img { 
      width: 100%; 
      height: 100%; 
      object-fit: contain; 
      object-position: center; 
      background: white;
    }
    
    .product-info { 
      padding: 15px; 
    }
    
    .product-title { 
      margin: 0 0 8px 0; 
      font-size: 16px; 
      font-weight: 600; 
      color: #333; 
    }
    
    .product-description { 
      color: #666; 
      font-size: 14px; 
      margin: 0 0 10px 0; 
      display: -webkit-box; 
      -webkit-line-clamp: 2; 
      -webkit-box-orient: vertical; 
      overflow: hidden; 
    }
    
    .product-details { 
      display: flex; 
      justify-content: space-between; 
      align-items: center; 
      margin-bottom: 8px; 
    }
    
    .product-price { 
      font-size: 18px; 
      font-weight: 700; 
      color: #28a745; 
    }
    
    .product-category { 
      background: #e9ecef; 
      padding: 2px 8px; 
      border-radius: 12px; 
      font-size: 12px; 
      color: #495057; 
    }
    
    .product-stock { 
      margin-bottom: 15px; 
    }
    
    .stock-low { 
      color: #dc3545; 
      font-weight: 600; 
    }
    
    .stock-medium { 
      color: #ffc107; 
      font-weight: 600; 
    }
    
    .stock-good { 
      color: #28a745; 
    }
    
    .product-actions { 
      display: flex; 
      gap: 8px; 
    }
    
    .no-products { 
      text-align: center; 
      padding: 40px; 
      color: #666; 
      font-style: italic; 
    }
    
    .btn { 
      padding: 8px 16px; 
      border: none; 
      border-radius: 4px; 
      cursor: pointer; 
      font-size: 14px; 
      transition: background-color 0.2s; 
    }
    
    .btn-sm { 
      padding: 5px 10px; 
      font-size: 12px; 
    }
    
    .btn-primary { background: #007bff; color: white; }
    .btn-primary:hover { background: #0056b3; }
    
    .btn-success { background: #28a745; color: white; }
    .btn-success:hover { background: #1e7e34; }
    
    .btn-secondary { background: #6c757d; color: white; }
    .btn-secondary:hover { background: #545b62; }
    
    .btn-outline-primary { 
      background: transparent; 
      color: #007bff; 
      border: 1px solid #007bff; 
    }
    .btn-outline-primary:hover { 
      background: #007bff; 
      color: white; 
    }
    
    .btn-outline-danger { 
      background: transparent; 
      color: #dc3545; 
      border: 1px solid #dc3545; 
    }
    .btn-outline-danger:hover { 
      background: #dc3545; 
      color: white; 
    }
    
    .form-control { 
      width: 100%; 
      padding: 8px 12px; 
      border: 1px solid #ddd; 
      border-radius: 4px; 
      font-size: 14px; 
    }
    
    .form-control:focus { 
      outline: none; 
      border-color: #007bff; 
      box-shadow: 0 0 0 2px rgba(0,123,255,0.25); 
    }
    
    .mb-3 { margin-bottom: 1rem; }
    
    @media (max-width: 768px) {
      .filter-row { 
        grid-template-columns: 1fr; 
        gap: 10px; 
      }
      
      .form-row { 
        grid-template-columns: 1fr; 
      }
      
      .products-grid { 
        grid-template-columns: 1fr; 
      }
    }
  `]
})
export class ProductsComponent implements OnInit {
  products: Product[] = [];
  filteredProducts: Product[] = [];
  showForm: boolean = false;
  editingProduct: Product | null = null;
  
  // Filter properties
  searchTerm: string = '';
  selectedCategory: string = '';
  showOnlyActive: boolean = false;
  
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
      next: (products: Product[]) => {
        this.products = products;
        this.filteredProducts = products;
        this.applyFilters();
      },
      error: (error: any) => {
        console.error('Fehler beim Laden der Produkte:', error);
      }
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
        next: (products: Product[]) => {
          this.filteredProducts = products;
        },
        error: (error: any) => {
          console.error('Fehler beim Filtern der Produkte:', error);
          // Fallback auf lokale Filterung
          this.filteredProducts = this.filterProductsLocally();
        }
      });
    } else {
      this.filteredProducts = [...this.products];
    }
  }

  private hasActiveFilters(filter: ProductFilter): boolean {
    return !!(filter.search || filter.category || filter.active);
  }

  private filterProductsLocally(): Product[] {
    return this.products.filter(product => {
      const matchesSearch = !this.searchTerm || 
        product.name.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        product.description?.toLowerCase().includes(this.searchTerm.toLowerCase());
      
      const matchesCategory = !this.selectedCategory || product.category === this.selectedCategory;
      const matchesActive = !this.showOnlyActive || product.active;
      
      return matchesSearch && matchesCategory && matchesActive;
    });
  }

  getImageUrl(imageUrl: string | undefined): string {
    if (!imageUrl) {
      return `${environment.apiUrl.replace('/api', '')}/images/placeholder.jpg?t=` + Date.now();
    }
    return `${environment.apiUrl.replace('/api', '')}${imageUrl}?t=` + Date.now();
  }

  getStockStatusClass(stockQuantity: number): string {
    if (stockQuantity <= 5) return 'stock-low';
    if (stockQuantity <= 15) return 'stock-medium';
    return 'stock-good';
  }

  addProduct(): void {
    if (this.editingProduct) {
      this.updateProduct();
    } else {
      this.createProduct();
    }
  }

  private createProduct(): void {
    this.productService.create(this.newProduct).subscribe({
      next: (product: Product) => {
        this.products.push(product);
        this.applyFilters();
        this.resetForm();
      },
      error: (error: any) => {
        console.error('Fehler beim Erstellen des Produkts:', error);
      }
    });
  }

  private updateProduct(): void {
    if (!this.editingProduct?.id) return;

    const updateRequest: UpdateProductRequest = {
      id: this.editingProduct.id,
      name: this.newProduct.name,
      description: this.newProduct.description,
      price: this.newProduct.price,
      stockQuantity: this.newProduct.stockQuantity,
      category: this.newProduct.category,
      imageUrl: this.newProduct.imageUrl,
      active: this.newProduct.active
    };

    const productId = typeof this.editingProduct.id === 'string' ? parseInt(this.editingProduct.id) : this.editingProduct.id;
    this.productService.update(productId, updateRequest).subscribe({
      next: (updatedProduct: Product) => {
        const index = this.products.findIndex(p => p.id === updatedProduct.id);
        if (index > -1) {
          this.products[index] = updatedProduct;
        }
        this.applyFilters();
        this.resetForm();
      },
      error: (error: any) => {
        console.error('Fehler beim Aktualisieren des Produkts:', error);
      }
    });
  }

  editProduct(product: Product): void {
    this.editingProduct = product;
    this.newProduct = {
      name: product.name,
      description: product.description || '',
      price: product.price,
      stockQuantity: product.stockQuantity,
      category: product.category as ProductCategory,
      imageUrl: product.imageUrl || '',
      active: product.active
    };
    this.showForm = true;
  }

  deleteProduct(id: string | number): void {
    if (confirm('Produkt löschen?')) {
      const productId = typeof id === 'string' ? parseInt(id) : id;
      this.productService.delete(productId).subscribe({
        next: (success: boolean) => {
          if (success) {
            this.products = this.products.filter(p => p.id !== productId);
            this.applyFilters();
          }
        },
        error: (error: any) => {
          console.error('Fehler beim Löschen:', error);
        }
      });
    }
  }

  private resetForm(): void {
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
}