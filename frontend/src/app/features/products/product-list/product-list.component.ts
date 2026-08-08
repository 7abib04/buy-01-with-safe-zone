import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProductService } from '../../../core/services/product.service';
import { Product, ProductCategory } from '../../../core/models/product.model';
import { ProductCardComponent } from '../../../shared/components/product-card/product-card.component';
import { SkeletonComponent } from '../../../shared/components/skeleton/skeleton.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { PaginationComponent } from '../../../shared/components/pagination/pagination.component';
import { ToastService } from '../../../core/services/toast.service';

type SortOption = 'newest' | 'oldest' | 'price_asc' | 'price_desc';

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [CommonModule, FormsModule, ProductCardComponent, SkeletonComponent, EmptyStateComponent, PaginationComponent],
  templateUrl: './product-list.component.html',
  styleUrl: './product-list.component.scss'
})
export class ProductListComponent implements OnInit {
  productService = inject(ProductService);
  toastService = inject(ToastService);

  products: Product[] = [];
  isLoading = true;
  skeletonArray = Array(8).fill(0);

  categories: string[] = [];
  catalogMinPrice = 0;
  catalogMaxPrice = 1000;

  searchTerm = '';
  categoryFilter: ProductCategory | '' = '';
  selectedMinPrice = 0;
  selectedMaxPrice = 1000;
  sortOption: SortOption = 'newest';

  page = 0;
  size = 12;
  totalPages = 0;
  totalElements = 0;

  hasLoadedOnce = false;

  ngOnInit(): void {
    this.load();
  }

  get hasActiveFilters(): boolean {
    return (
      this.searchTerm.trim().length > 0 ||
      this.categoryFilter !== '' ||
      this.selectedMinPrice > this.catalogMinPrice ||
      this.selectedMaxPrice < this.catalogMaxPrice ||
      this.sortOption !== 'newest'
    );
  }

  get minThumbPercent(): number {
    return this.toPercent(this.selectedMinPrice);
  }

  get maxThumbPercent(): number {
    return this.toPercent(this.selectedMaxPrice);
  }

  get rangeFillLeftPercent(): number {
    return Math.min(this.minThumbPercent, this.maxThumbPercent);
  }

  get rangeFillWidthPercent(): number {
    return Math.max(this.maxThumbPercent - this.minThumbPercent, 0);
  }

  load(): void {
    this.isLoading = true;
    this.productService
      .searchProducts({
        q: this.searchTerm.trim() || undefined,
        category: this.categoryFilter || undefined,
        minPrice: this.selectedMinPrice > this.catalogMinPrice ? this.selectedMinPrice : undefined,
        maxPrice: this.selectedMaxPrice < this.catalogMaxPrice ? this.selectedMaxPrice : undefined,
        sort: this.sortOption,
        page: this.page,
        size: this.size
      })
      .subscribe({
        next: (result) => {
          this.products = result.content;
          this.totalPages = result.totalPages;
          this.totalElements = result.totalElements;
          this.categories = result.categories;

          if (!this.hasLoadedOnce) {
            this.catalogMinPrice = result.minPrice;
            this.catalogMaxPrice = result.maxPrice || 1000;
            this.selectedMinPrice = this.catalogMinPrice;
            this.selectedMaxPrice = this.catalogMaxPrice;
            this.hasLoadedOnce = true;
          }

          this.isLoading = false;
        },
        error: () => {
          this.toastService.show('Failed to load products.', 'error');
          this.isLoading = false;
        }
      });
  }

  onSearchSubmit(): void {
    this.page = 0;
    this.load();
  }

  onFilterChange(): void {
    this.page = 0;
    this.load();
  }

  onPageChange(page: number): void {
    this.page = page;
    this.load();
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.categoryFilter = '';
    this.selectedMinPrice = this.catalogMinPrice;
    this.selectedMaxPrice = this.catalogMaxPrice;
    this.sortOption = 'newest';
    this.page = 0;
    this.load();
  }

  onMinPriceInput(value: string | number | null): void {
    const nextMin = this.normalizePrice(value, this.catalogMinPrice);
    this.selectedMinPrice = Math.min(nextMin, this.selectedMaxPrice);
  }

  onMaxPriceInput(value: string | number | null): void {
    const nextMax = this.normalizePrice(value, this.catalogMaxPrice);
    this.selectedMaxPrice = Math.max(nextMax, this.selectedMinPrice);
  }

  onPriceCommit(): void {
    this.onFilterChange();
  }

  trackByProductId(index: number, product: Product): string {
    return product.id;
  }

  private normalizePrice(value: string | number | null | undefined, fallback: number): number {
    if (value === null || value === undefined || value === '') {
      return fallback;
    }

    const parsedValue = typeof value === 'number' ? value : Number(String(value).replace(',', '.'));

    if (!Number.isFinite(parsedValue)) {
      return fallback;
    }

    const clamped = Math.min(Math.max(parsedValue, this.catalogMinPrice), this.catalogMaxPrice);
    return Number(clamped.toFixed(2));
  }

  private toPercent(value: number): number {
    const span = this.catalogMaxPrice - this.catalogMinPrice;
    if (span <= 0) {
      return 0;
    }

    return ((value - this.catalogMinPrice) / span) * 100;
  }
}
