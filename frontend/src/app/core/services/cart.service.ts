import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, catchError, map, tap, throwError } from 'rxjs';
import { Cart } from '../models/cart.model';
import { environment } from '../../../environments/environment';

const EMPTY_CART: Cart = { items: [], totalItems: 0, totalAmount: 0 };

@Injectable({
  providedIn: 'root'
})
export class CartService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  private readonly cartSubject = new BehaviorSubject<Cart>(EMPTY_CART);
  public cart$ = this.cartSubject.asObservable();
  public itemCount$ = this.cart$.pipe(map((cart) => cart.totalItems));

  get currentCart(): Cart {
    return this.cartSubject.value;
  }

  refresh(): Observable<Cart> {
    return this.http.get<Cart>(`${this.apiUrl}/cart`).pipe(
      tap((cart) => this.cartSubject.next(cart)),
      catchError((error) => throwError(() => error))
    );
  }

  addItem(productId: string, quantity: number): Observable<Cart> {
    return this.http.post<Cart>(`${this.apiUrl}/cart/items`, { productId, quantity }).pipe(
      tap((cart) => this.cartSubject.next(cart))
    );
  }

  updateItemQuantity(productId: string, quantity: number): Observable<Cart> {
    return this.http.put<Cart>(`${this.apiUrl}/cart/items/${productId}`, { quantity }).pipe(
      tap((cart) => this.cartSubject.next(cart))
    );
  }

  removeItem(productId: string): Observable<Cart> {
    return this.http.delete<Cart>(`${this.apiUrl}/cart/items/${productId}`).pipe(
      tap((cart) => this.cartSubject.next(cart))
    );
  }

  clear(): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/cart`).pipe(
      tap(() => this.cartSubject.next(EMPTY_CART))
    );
  }

  reset(): void {
    this.cartSubject.next(EMPTY_CART);
  }
}
