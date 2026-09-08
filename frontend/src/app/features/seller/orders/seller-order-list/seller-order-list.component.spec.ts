import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SellerOrderListComponent } from './seller-order-list.component';
import { OrderService } from '../../../../core/services/order.service';
import { ToastService } from '../../../../core/services/toast.service';
import { AuthService } from '../../../../core/services/auth.service';
import { Order, OrderPage } from '../../../../core/models/order.model';

describe('SellerOrderListComponent', () => {
  let orderServiceSpy: jasmine.SpyObj<OrderService>;
  let toastServiceSpy: jasmine.SpyObj<ToastService>;
  let authServiceStub: { currentUserValue: null };

  const emptyPage: OrderPage = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 };

  function order(overrides: Partial<Order>): Order {
    return {
      id: 'order-1', buyerId: 'b1', buyerName: 'Jane', items: [], totalAmount: 50,
      status: 'PENDING', paymentMethod: 'CASH_ON_DELIVERY',
      shippingAddress: { fullName: 'Jane', phone: '1', line1: 'a', city: 'c', postalCode: '0', country: 'x' },
      statusHistory: [], createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
      ...overrides
    };
  }

  function createComponent() {
    TestBed.configureTestingModule({
      imports: [SellerOrderListComponent],
      providers: [
        { provide: OrderService, useValue: orderServiceSpy },
        { provide: ToastService, useValue: toastServiceSpy },
        { provide: AuthService, useValue: authServiceStub },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => null } } } }
      ]
    });
    return TestBed.createComponent(SellerOrderListComponent).componentInstance;
  }

  beforeEach(() => {
    orderServiceSpy = jasmine.createSpyObj('OrderService', ['getSellerOrders']);
    toastServiceSpy = jasmine.createSpyObj('ToastService', ['show']);
    authServiceStub = { currentUserValue: null };
    orderServiceSpy.getSellerOrders.and.returnValue(of(emptyPage));
  });

  it('ngOnInit loads seller orders', () => {
    const component = createComponent();
    component.ngOnInit();
    expect(orderServiceSpy.getSellerOrders).toHaveBeenCalled();
    expect(component.isLoading).toBeFalse();
  });

  it('load sends filters and pagination', () => {
    const component = createComponent();
    component.statusFilter = 'SHIPPED';
    component.searchTerm = ' case ';
    component.page = 1;
    component.size = 20;

    component.load();

    expect(orderServiceSpy.getSellerOrders).toHaveBeenCalledWith({
      status: 'SHIPPED', q: 'case', page: 1, size: 20
    });
  });

  it('shows a toast when loading fails', () => {
    orderServiceSpy.getSellerOrders.and.returnValue(throwError(() => ({})));
    const component = createComponent();

    component.load();

    expect(component.isLoading).toBeFalse();
    expect(toastServiceSpy.show).toHaveBeenCalledWith('Could not load your orders.', 'error');
  });

  it('pendingCount counts only PENDING orders', () => {
    const component = createComponent();
    component.orders = [order({ status: 'PENDING' }), order({ status: 'SHIPPED' }), order({ status: 'PENDING' })];

    expect(component.pendingCount).toBe(2);
  });

  it('revenueThisPage sums totals excluding cancelled orders', () => {
    const component = createComponent();
    component.orders = [
      order({ status: 'DELIVERED', totalAmount: 30 }),
      order({ status: 'CANCELLED', totalAmount: 999 }),
      order({ status: 'PENDING', totalAmount: 20 })
    ];

    expect(component.revenueThisPage).toBe(50);
  });

  it('onSearchSubmit and onStatusChange reset to page 0', () => {
    const component = createComponent();
    component.page = 5;
    component.onSearchSubmit();
    expect(component.page).toBe(0);

    component.page = 5;
    component.onStatusChange();
    expect(component.page).toBe(0);
  });

  it('onPageChange loads the requested page', () => {
    const component = createComponent();
    component.onPageChange(2);
    expect(component.page).toBe(2);
  });

  it('trackByOrderId returns the order id', () => {
    const component = createComponent();
    expect(component.trackByOrderId(0, order({ id: 'order-42' }))).toBe('order-42');
  });
});
