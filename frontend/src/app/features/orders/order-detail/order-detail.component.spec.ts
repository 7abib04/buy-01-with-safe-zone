import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OrderDetailComponent } from './order-detail.component';
import { OrderService } from '../../../core/services/order.service';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { Order } from '../../../core/models/order.model';

describe('OrderDetailComponent', () => {
  let orderServiceSpy: jasmine.SpyObj<OrderService>;
  let authServiceStub: { currentUserValue: { id: string } | null };
  let toastServiceSpy: jasmine.SpyObj<ToastService>;
  let routerSpy: jasmine.SpyObj<Router>;
  let activatedRouteStub: { snapshot: { paramMap: { get: (key: string) => string | null } } };

  function baseOrder(overrides: Partial<Order> = {}): Order {
    return {
      id: 'order-1',
      buyerId: 'buyer-1',
      buyerName: 'Jane',
      items: [],
      totalAmount: 100,
      status: 'PENDING',
      paymentMethod: 'CASH_ON_DELIVERY',
      shippingAddress: { fullName: 'Jane', phone: '123', line1: '1 Main St', city: 'City', postalCode: '0', country: 'C' },
      statusHistory: [],
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      ...overrides
    };
  }

  function createComponent(orderId: string | null = 'order-1') {
    activatedRouteStub = { snapshot: { paramMap: { get: () => orderId } } };
    TestBed.configureTestingModule({
      imports: [OrderDetailComponent],
      providers: [
        { provide: ActivatedRoute, useValue: activatedRouteStub },
        { provide: Router, useValue: routerSpy },
        { provide: OrderService, useValue: orderServiceSpy },
        { provide: AuthService, useValue: authServiceStub },
        { provide: ToastService, useValue: toastServiceSpy }
      ]
    });
    return TestBed.createComponent(OrderDetailComponent).componentInstance;
  }

  beforeEach(() => {
    orderServiceSpy = jasmine.createSpyObj('OrderService', ['getOrder', 'cancelOrder', 'redoOrder', 'removeOrder']);
    authServiceStub = { currentUserValue: { id: 'buyer-1' } };
    toastServiceSpy = jasmine.createSpyObj('ToastService', ['show']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
  });

  it('ngOnInit loads the order when an id is present', () => {
    orderServiceSpy.getOrder.and.returnValue(of(baseOrder()));
    const component = createComponent('order-1');

    component.ngOnInit();

    expect(orderServiceSpy.getOrder).toHaveBeenCalledWith('order-1');
    expect(component.order?.id).toBe('order-1');
    expect(component.isLoading).toBeFalse();
  });

  it('ngOnInit does nothing when there is no id', () => {
    const component = createComponent(null);

    component.ngOnInit();

    expect(orderServiceSpy.getOrder).not.toHaveBeenCalled();
  });

  it('marks not-found on a 404 error', () => {
    orderServiceSpy.getOrder.and.returnValue(throwError(() => ({ status: 404 })));
    const component = createComponent();

    component.ngOnInit();

    expect(component.isNotFound).toBeTrue();
    expect(toastServiceSpy.show).not.toHaveBeenCalled();
  });

  it('shows a toast for non-404 load errors', () => {
    orderServiceSpy.getOrder.and.returnValue(throwError(() => ({ status: 500 })));
    const component = createComponent();

    component.ngOnInit();

    expect(component.isNotFound).toBeFalse();
    expect(toastServiceSpy.show).toHaveBeenCalledWith('Could not load this order.', 'error');
  });

  it('isOwner is true only for the buyer who placed the order', () => {
    orderServiceSpy.getOrder.and.returnValue(of(baseOrder()));
    const component = createComponent();
    component.ngOnInit();

    expect(component.isOwner).toBeTrue();

    authServiceStub.currentUserValue = { id: 'someone-else' };
    expect(component.isOwner).toBeFalse();
  });

  it('canCancel is true only for PENDING/CONFIRMED owned orders', () => {
    orderServiceSpy.getOrder.and.returnValue(of(baseOrder({ status: 'PENDING' })));
    const component = createComponent();
    component.ngOnInit();
    expect(component.canCancel).toBeTrue();

    component.order = baseOrder({ status: 'SHIPPED' });
    expect(component.canCancel).toBeFalse();
  });

  it('canRedo and canRemove are true only for CANCELLED/DELIVERED owned orders', () => {
    orderServiceSpy.getOrder.and.returnValue(of(baseOrder({ status: 'DELIVERED' })));
    const component = createComponent();
    component.ngOnInit();

    expect(component.canRedo).toBeTrue();
    expect(component.canRemove).toBeTrue();

    component.order = baseOrder({ status: 'PENDING' });
    expect(component.canRedo).toBeFalse();
    expect(component.canRemove).toBeFalse();
  });

  it('timelineState returns upcoming when there is no order or it is cancelled', () => {
    const component = createComponent();
    expect(component.timelineState('PENDING')).toBe('upcoming');

    component.order = baseOrder({ status: 'CANCELLED' });
    expect(component.timelineState('PENDING')).toBe('upcoming');
  });

  it('timelineState marks done/active/upcoming relative to the current status', () => {
    const component = createComponent();
    component.order = baseOrder({ status: 'SHIPPED' });

    expect(component.timelineState('PENDING')).toBe('done');
    expect(component.timelineState('SHIPPED')).toBe('active');
    expect(component.timelineState('DELIVERED')).toBe('upcoming');
  });

  it('cancelOrder updates the order and shows a success toast', () => {
    orderServiceSpy.getOrder.and.returnValue(of(baseOrder()));
    orderServiceSpy.cancelOrder.and.returnValue(of(baseOrder({ status: 'CANCELLED' })));
    const component = createComponent();
    component.ngOnInit();

    component.cancelOrder();

    expect(component.order?.status).toBe('CANCELLED');
    expect(component.isCancelling).toBeFalse();
    expect(toastServiceSpy.show).toHaveBeenCalledWith('Order cancelled.', 'success');
  });

  it('cancelOrder shows an error toast on failure', () => {
    orderServiceSpy.getOrder.and.returnValue(of(baseOrder()));
    orderServiceSpy.cancelOrder.and.returnValue(throwError(() => ({})));
    const component = createComponent();
    component.ngOnInit();

    component.cancelOrder();

    expect(component.isCancelling).toBeFalse();
    expect(toastServiceSpy.show).toHaveBeenCalledWith('Could not cancel this order.', 'error');
  });

  it('cancelOrder does nothing when there is no loaded order', () => {
    const component = createComponent();
    component.cancelOrder();
    expect(orderServiceSpy.cancelOrder).not.toHaveBeenCalled();
  });

  it('redoOrder navigates to the cart on success', () => {
    orderServiceSpy.getOrder.and.returnValue(of(baseOrder()));
    orderServiceSpy.redoOrder.and.returnValue(of({ items: [], totalItems: 0, totalAmount: 0 }));
    const component = createComponent();
    component.ngOnInit();

    component.redoOrder();

    expect(component.isRedoing).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/cart']);
  });

  it('redoOrder shows an error toast on failure', () => {
    orderServiceSpy.getOrder.and.returnValue(of(baseOrder()));
    orderServiceSpy.redoOrder.and.returnValue(throwError(() => ({ error: { message: 'nope' } })));
    const component = createComponent();
    component.ngOnInit();

    component.redoOrder();

    expect(toastServiceSpy.show).toHaveBeenCalledWith('nope', 'error');
  });

  it('removeOrder navigates to the orders list on success', () => {
    orderServiceSpy.getOrder.and.returnValue(of(baseOrder()));
    orderServiceSpy.removeOrder.and.returnValue(of(undefined));
    const component = createComponent();
    component.ngOnInit();

    component.removeOrder();

    expect(component.isRemoving).toBeFalse();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/orders']);
  });

  it('removeOrder shows an error toast on failure', () => {
    orderServiceSpy.getOrder.and.returnValue(of(baseOrder()));
    orderServiceSpy.removeOrder.and.returnValue(throwError(() => ({})));
    const component = createComponent();
    component.ngOnInit();

    component.removeOrder();

    expect(toastServiceSpy.show).toHaveBeenCalledWith('Could not remove this order.', 'error');
  });
});
