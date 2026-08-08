import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, map, shareReplay, tap, throwError } from 'rxjs';
import { Product, ProductCategory } from '../models/product.model';
import { environment } from '../../../environments/environment';
import { normalizeManagedMediaUrls } from '../utils/media-url';

interface ProductResponse {
  id: string;
  name: string;
  description: string;
  price: number;
  quantity: number;
  sellerId: string;
  category?: ProductCategory | null;
  imageUrls?: string[] | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProductSearchParams {
  q?: string;
  category?: ProductCategory;
  minPrice?: number;
  maxPrice?: number;
  sort?: 'newest' | 'oldest' | 'price_asc' | 'price_desc';
  page?: number;
  size?: number;
}

export interface ProductSearchResult {
  content: Product[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  categories: string[];
  minPrice: number;
  maxPrice: number;
}

interface RawProductSearchResponse {
  content: ProductResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  categories: string[];
  minPrice: number;
  maxPrice: number;
}

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;
  private allProducts$?: Observable<Product[]>;
  private myProducts$?: Observable<Product[]>;
  private productByIdCache = new Map<string, Observable<Product>>();
  private productsBySellerCache = new Map<string, Observable<Product[]>>();

  getAll(): Observable<Product[]> {
    if (this.allProducts$) {
      return this.allProducts$;
    }

    this.allProducts$ = this.http
      .get<ProductResponse[]>(`${this.apiUrl}/products`)
      .pipe(
        map((products) => products.map((product) => this.normalizeProduct(product))),
        catchError((error) => {
          this.allProducts$ = undefined;
          return throwError(() => error);
        }),
        shareReplay(1)
      );

    return this.allProducts$;
  }

  getById(id: string): Observable<Product> {
    const cachedProduct = this.productByIdCache.get(id);
    if (cachedProduct) {
      return cachedProduct;
    }

    const request$ = this.http
      .get<ProductResponse>(`${this.apiUrl}/products/${id}`)
      .pipe(
        map((product) => this.normalizeProduct(product)),
        catchError((error) => {
          this.productByIdCache.delete(id);
          return throwError(() => error);
        }),
        shareReplay(1)
      );

    this.productByIdCache.set(id, request$);
    return request$;
  }

  getMyProducts(): Observable<Product[]> {
    if (this.myProducts$) {
      return this.myProducts$;
    }

    this.myProducts$ = this.http
      .get<ProductResponse[]>(`${this.apiUrl}/products/me`)
      .pipe(
        map((products) => products.map((product) => this.normalizeProduct(product))),
        catchError((error) => {
          this.myProducts$ = undefined;
          return throwError(() => error);
        }),
        shareReplay(1)
      );

    return this.myProducts$;
  }

  getAllBySeller(): Observable<Product[]> {
    return this.getMyProducts();
  }

  getBySellerId(sellerId: string): Observable<Product[]> {
    const cachedProducts = this.productsBySellerCache.get(sellerId);
    if (cachedProducts) {
      return cachedProducts;
    }

    const request$ = this.http
      .get<ProductResponse[]>(`${this.apiUrl}/products/seller/${sellerId}`)
      .pipe(
        map((products) => products.map((product) => this.normalizeProduct(product))),
        catchError((error) => {
          this.productsBySellerCache.delete(sellerId);
          return throwError(() => error);
        }),
        shareReplay(1)
      );

    this.productsBySellerCache.set(sellerId, request$);
    return request$;
  }

  searchProducts(params: ProductSearchParams = {}): Observable<ProductSearchResult> {
    let httpParams = new HttpParams();
    if (params.q) httpParams = httpParams.set('q', params.q);
    if (params.category) httpParams = httpParams.set('category', params.category);
    if (params.minPrice !== undefined) httpParams = httpParams.set('minPrice', params.minPrice);
    if (params.maxPrice !== undefined) httpParams = httpParams.set('maxPrice', params.maxPrice);
    if (params.sort) httpParams = httpParams.set('sort', params.sort);
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page);
    if (params.size !== undefined) httpParams = httpParams.set('size', params.size);

    return this.http
      .get<RawProductSearchResponse>(`${this.apiUrl}/products/search`, { params: httpParams })
      .pipe(
        map((result) => ({
          ...result,
          content: result.content.map((product) => this.normalizeProduct(product))
        }))
      );
  }

  create(product: Partial<Product>): Observable<Product> {
    return this.http.post<ProductResponse>(`${this.apiUrl}/products`, product).pipe(
      map((createdProduct) => this.normalizeProduct(createdProduct)),
      tap(() => this.invalidateCache())
    );
  }

  update(id: string, product: Partial<Product>): Observable<Product> {
    return this.http.put<ProductResponse>(`${this.apiUrl}/products/${id}`, product).pipe(
      map((updatedProduct) => this.normalizeProduct(updatedProduct)),
      tap(() => this.invalidateCache(id))
    );
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/products/${id}`).pipe(
      tap(() => this.invalidateCache(id))
    );
  }

  private normalizeProduct(product: ProductResponse): Product {
    return {
      ...product,
      category: product.category ?? undefined,
      imageUrls: normalizeManagedMediaUrls(product.imageUrls)
    };
  }

  invalidateCache(productId?: string): void {
    this.allProducts$ = undefined;
    this.myProducts$ = undefined;
    this.productsBySellerCache.clear();

    if (productId) {
      this.productByIdCache.delete(productId);
      return;
    }

    this.productByIdCache.clear();
  }
}
