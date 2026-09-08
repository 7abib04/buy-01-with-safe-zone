import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { OrderService } from '../../../../core/services/order.service';
import { ToastService } from '../../../../core/services/toast.service';
import { Order, OrderStatus } from '../../../../core/models/order.model';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { SkeletonComponent } from '../../../../shared/components/skeleton/skeleton.component';
import { PaginationComponent } from '../../../../shared/components/pagination/pagination.component';
import { SellerPortalShellComponent } from '../../../../shared/components/seller-portal-shell/seller-portal-shell.component';

@Component({
  selector: 'app-seller-order-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    EmptyStateComponent,
    SkeletonComponent,
    PaginationComponent,
    SellerPortalShellComponent
  ],
  templateUrl: './seller-order-list.component.html',
  styleUrl: './seller-order-list.component.scss'
})
export class SellerOrderListComponent implements OnInit {
  private readonly orderService = inject(OrderService);
  private readonly toastService = inject(ToastService);

  readonly statusOptions: OrderStatus[] = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'];
  readonly skeletonArray = new Array(5).fill(0);

  orders: Order[] = [];
  isLoading = true;
  page = 0;
  size = 10;
  totalPages = 0;
  totalElements = 0;
  searchTerm = '';
  statusFilter: OrderStatus | '' = '';

  get pendingCount(): number {
    return this.orders.filter((order) => order.status === 'PENDING').length;
  }

  get revenueThisPage(): number {
    return this.orders
      .filter((order) => order.status !== 'CANCELLED')
      .reduce((sum, order) => sum + order.totalAmount, 0);
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.isLoading = true;
    this.orderService
      .getSellerOrders({
        status: this.statusFilter || undefined,
        q: this.searchTerm.trim() || undefined,
        page: this.page,
        size: this.size
      })
      .subscribe({
        next: (result) => {
          this.orders = result.content;
          this.totalPages = result.totalPages;
          this.totalElements = result.totalElements;
          this.isLoading = false;
        },
        error: () => {
          this.isLoading = false;
          this.toastService.show('Could not load your orders.', 'error');
        }
      });
  }

  onSearchSubmit(): void {
    this.page = 0;
    this.load();
  }

  onStatusChange(): void {
    this.page = 0;
    this.load();
  }

  onPageChange(page: number): void {
    this.page = page;
    this.load();
  }

  trackByOrderId(index: number, order: Order): string {
    return order.id;
  }
}
