import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { SellerOrderDetailComponent } from './seller-order-detail.component';
import { OrderService } from '../../../../core/services/order.service';
import { ToastService } from '../../../../core/services/toast.service';
import { AuthService } from '../../../../core/services/auth.service';
import { Order } from '../../../../core/models/order.model';

describe('SellerOrderDetailComponent', () => {
  let orderServiceSpy: jasmine.SpyObj<OrderService>;
  let toastServiceSpy: jasmine.SpyObj<ToastService>;
  let authServiceStub: { currentUserValue: null };
  let activatedRouteStub: { snapshot: { paramMap: { get: (key: string) => string | null } } };

  function order(overrides: Partial<Order>): Order {
    return {
      id: 'order-1', buyerId: 'b1', buyerName: 'Jane', items: [], totalAmount: 50,
      status: 'PENDING', paymentMethod: 'CASH_ON_DELIVERY',
      shippingAddress: { fullName: 'Jane', phone: '1', line1: 'a', city: 'c', postalCode: '0', country: 'x' },
      statusHistory: [], createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
      ...overrides
    };
  }

  function createComponent(orderId: string | null = 'order-1') {
    activatedRouteStub = { snapshot: { paramMap: { get: () => orderId } } };
    TestBed.configureTestingModule({
      imports: [SellerOrderDetailComponent],
      providers: [
        { provide: ActivatedRoute, useValue: activatedRouteStub },
        { provide: OrderService, useValue: orderServiceSpy },
        { provide: ToastService, useValue: toastServiceSpy },
        { provide: AuthService, useValue: authServiceStub }
      ]
    });
    return TestBed.createComponent(SellerOrderDetailComponent).componentInstance;
  }

  beforeEach(() => {
    orderServiceSpy = jasmine.createSpyObj('OrderService', ['getOrder', 'updateOrderStatus']);
    toastServiceSpy = jasmine.createSpyObj('ToastService', ['show']);
    authServiceStub = { currentUserValue: null };
  });

  it('ngOnInit loads the order when an id is present', () => {
    orderServiceSpy.getOrder.and.returnValue(of(order({})));
    const component = createComponent('order-1');

    component.ngOnInit();

    expect(orderServiceSpy.getOrder).toHaveBeenCalledWith('order-1');
    expect(component.isLoading).toBeFalse();
  });

  it('ngOnInit does nothing without an id', () => {
    const component = createComponent(null);
    component.ngOnInit();
    expect(orderServiceSpy.getOrder).not.toHaveBeenCalled();
  });

  it('marks not-found on a 404', () => {
    orderServiceSpy.getOrder.and.returnValue(throwError(() => ({ status: 404 })));
    const component = createComponent();

    component.ngOnInit();

    expect(component.isNotFound).toBeTrue();
  });

  it('shows a toast for other load errors', () => {
    orderServiceSpy.getOrder.and.returnValue(throwError(() => ({ status: 500 })));
    const component = createComponent();

    component.ngOnInit();

    expect(toastServiceSpy.show).toHaveBeenCalledWith('Could not load this order.', 'error');
  });

  it('nextStatus follows the forward sequence', () => {
    orderServiceSpy.getOrder.and.returnValue(of(order({ status: 'CONFIRMED' })));
    const component = createComponent();
    component.ngOnInit();

    expect(component.nextStatus).toBe('SHIPPED');
  });

  it('nextStatus is null at the end of the sequence or for cancelled orders', () => {
    orderServiceSpy.getOrder.and.returnValue(of(order({ status: 'DELIVERED' })));
    const component = createComponent();
    component.ngOnInit();
    expect(component.nextStatus).toBeNull();

    component.order = order({ status: 'CANCELLED' });
    expect(component.nextStatus).toBeNull();
  });

  it('nextStatus is null when there is no order loaded', () => {
    const component = createComponent();
    expect(component.nextStatus).toBeNull();
  });

  it('advanceStatus updates the order and shows a success toast', () => {
    orderServiceSpy.getOrder.and.returnValue(of(order({ status: 'PENDING' })));
    orderServiceSpy.updateOrderStatus.and.returnValue(of(order({ status: 'CONFIRMED' })));
    const component = createComponent();
    component.ngOnInit();

    component.advanceStatus();

    expect(orderServiceSpy.updateOrderStatus).toHaveBeenCalledWith('order-1', 'CONFIRMED');
    expect(component.order?.status).toBe('CONFIRMED');
    expect(component.isUpdatingStatus).toBeFalse();
    expect(toastServiceSpy.show).toHaveBeenCalledWith('Order marked as CONFIRMED.', 'success');
  });

  it('advanceStatus does nothing when there is no next status', () => {
    orderServiceSpy.getOrder.and.returnValue(of(order({ status: 'DELIVERED' })));
    const component = createComponent();
    component.ngOnInit();

    component.advanceStatus();

    expect(orderServiceSpy.updateOrderStatus).not.toHaveBeenCalled();
  });

  it('advanceStatus shows an error toast on failure', () => {
    orderServiceSpy.getOrder.and.returnValue(of(order({ status: 'PENDING' })));
    orderServiceSpy.updateOrderStatus.and.returnValue(throwError(() => ({ error: { message: 'nope' } })));
    const component = createComponent();
    component.ngOnInit();

    component.advanceStatus();

    expect(component.isUpdatingStatus).toBeFalse();
    expect(toastServiceSpy.show).toHaveBeenCalledWith('nope', 'error');
  });
});
