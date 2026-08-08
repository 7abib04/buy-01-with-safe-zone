import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Order, ShippingAddress } from '../models/order.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  checkout(shippingAddress: ShippingAddress): Observable<Order> {
    return this.http.post<Order>(`${this.apiUrl}/orders/checkout`, { shippingAddress });
  }
}
