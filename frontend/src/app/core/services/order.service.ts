import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BuyerAnalytics, Order, OrderPage, OrderStatus, SellerAnalytics, ShippingAddress } from '../models/order.model';
import { Cart } from '../models/cart.model';
import { environment } from '../../../environments/environment';

export interface OrderSearchParams {
  status?: OrderStatus;
  q?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  checkout(shippingAddress: ShippingAddress): Observable<Order> {
    return this.http.post<Order>(`${this.apiUrl}/orders/checkout`, { shippingAddress });
  }

  getMyOrders(params: OrderSearchParams = {}): Observable<OrderPage> {
    return this.http.get<OrderPage>(`${this.apiUrl}/orders`, { params: this.toHttpParams(params) });
  }

  getSellerOrders(params: OrderSearchParams = {}): Observable<OrderPage> {
    return this.http.get<OrderPage>(`${this.apiUrl}/orders/seller`, { params: this.toHttpParams(params) });
  }

  getOrder(id: string): Observable<Order> {
    return this.http.get<Order>(`${this.apiUrl}/orders/${id}`);
  }

  cancelOrder(id: string): Observable<Order> {
    return this.http.patch<Order>(`${this.apiUrl}/orders/${id}/cancel`, {});
  }

  redoOrder(id: string): Observable<Cart> {
    return this.http.post<Cart>(`${this.apiUrl}/orders/${id}/redo`, {});
  }

  removeOrder(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/orders/${id}`);
  }

  updateOrderStatus(id: string, status: OrderStatus): Observable<Order> {
    return this.http.patch<Order>(`${this.apiUrl}/orders/${id}/status`, { status });
  }

  getBuyerAnalytics(): Observable<BuyerAnalytics> {
    return this.http.get<BuyerAnalytics>(`${this.apiUrl}/orders/analytics/me`);
  }

  getSellerAnalytics(): Observable<SellerAnalytics> {
    return this.http.get<SellerAnalytics>(`${this.apiUrl}/orders/analytics/seller`);
  }

  private toHttpParams(params: OrderSearchParams): HttpParams {
    let httpParams = new HttpParams();
    if (params.status) httpParams = httpParams.set('status', params.status);
    if (params.q) httpParams = httpParams.set('q', params.q);
    if (params.from) httpParams = httpParams.set('from', params.from);
    if (params.to) httpParams = httpParams.set('to', params.to);
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page);
    if (params.size !== undefined) httpParams = httpParams.set('size', params.size);
    return httpParams;
  }
}
