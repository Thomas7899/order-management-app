import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProductService } from '../services/product.service';
import { Product, ProductCategory, CreateProductRequest } from '../types/index';

@Component({
  selector: 'app-products',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container">
      <h2>Produktverwaltung</h2>
      
      <button (click)="showForm = !showForm" class="btn btn-primary mb-3">
        Neues Produkt hinzufügen
      </button>
      
      <div *ngIf="showForm" class="card">
        <div class="card-body">
          <h5>Produkt hinzufügen</h5>
          <form (ngSubmit)="addProduct()" #form="ngForm">
            <div class="mb-3">
              <label>Name:</label>
              <input type="text" [(ngModel)]="newProduct.name" name="name" required class="form-control">
            </div>
            <div class="mb-3">
              <label>Preis:</label>
              <input type="number" [(ngModel)]="newProduct.price" name="price" required class="form-control">
            </div>
            <div class="mb-3">
              <label>Kategorie:</label>
              <select [(ngModel)]="newProduct.category" name="category" required class="form-control">
                <option value="ELEKTRONIK">Elektronik</option>
                <option value="MOEBEL">Möbel</option>
                <option value="BELEUCHTUNG">Beleuchtung</option>
              </select>
            </div>
            <button type="submit" class="btn btn-success">Speichern</button>
            <button type="button" (click)="showForm = false" class="btn btn-secondary">Abbrechen</button>
          </form>
        </div>
      </div>
      
      <div class="products-grid">
        <div *ngFor="let product of products" class="card">
          <div class="card-body">
            <h5>{{ product.name }}</h5>
            <p>Preis: {{ product.price }} €</p>
            <p>Kategorie: {{ product.category }}</p>
            <button (click)="deleteProduct(product.id)" class="btn btn-danger">Löschen</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .container { padding: 20px; }
    .card { margin: 15px 0; }
    .products-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 20px; margin-top: 20px; }
    .btn { padding: 8px 16px; margin: 5px; border: none; border-radius: 4px; cursor: pointer; }
    .btn-primary { background: #007bff; color: white; }
    .btn-success { background: #28a745; color: white; }
    .btn-secondary { background: #6c757d; color: white; }
    .btn-danger { background: #dc3545; color: white; }
    .form-control { width: 100%; padding: 8px; margin: 5px 0; border: 1px solid #ddd; border-radius: 4px; }
    .mb-3 { margin-bottom: 1rem; }
  `]
})
export class ProductsComponent implements OnInit {
  products: Product[] = [];
  showForm = false;
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
      },
      error: (error: any) => {
        console.error('Fehler beim Laden der Produkte:', error);
      }
    });
  }

  addProduct(): void {
    this.productService.create(this.newProduct).subscribe({
      next: (product: Product) => {
        this.products.push(product);
        this.resetForm();
      },
      error: (error: any) => {
        console.error('Fehler beim Erstellen des Produkts:', error);
      }
    });
  }

  deleteProduct(id: string | number): void {
    if (confirm('Produkt löschen?')) {
      const productId = typeof id === 'string' ? parseInt(id) : id;
      this.productService.delete(productId).subscribe({
        next: (success: boolean) => {
          if (success) {
            this.products = this.products.filter(p => p.id !== productId);
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
    this.showForm = false;
  }
}