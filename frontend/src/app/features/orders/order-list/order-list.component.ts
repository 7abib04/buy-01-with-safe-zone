import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, PackageSearch } from 'lucide-angular';
import { OrderService } from '../../../core/services/order.service';
import { ToastService } from '../../../core/services/toast.service';
import { Order, OrderStatus } from '../../../core/models/order.model';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { SkeletonComponent } from '../../../shared/components/skeleton/skeleton.component';
import { PaginationComponent } from '../../../shared/components/pagination/pagination.component';

@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, LucideAngularModule, EmptyStateComponent, SkeletonComponent, PaginationComponent],
  templateUrl: './order-list.component.html',
  styleUrl: './order-list.component.scss'
})
export class OrderListComponent implements OnInit {
  private orderService = inject(OrderService);
  private toastService = inject(ToastService);

  readonly PackageSearchIcon = PackageSearch;
  readonly statusOptions: OrderStatus[] = ['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'];
  readonly skeletonArray = Array(4).fill(0);

  orders: Order[] = [];
  isLoading = true;
  page = 0;
  size = 10;
  totalPages = 0;
  totalElements = 0;
  searchTerm = '';
  statusFilter: OrderStatus | '' = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.isLoading = true;
    this.orderService
      .getMyOrders({
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
