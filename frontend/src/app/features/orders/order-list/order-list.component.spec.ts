import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { OrderListComponent } from './order-list.component';
import { OrderService } from '../../../core/services/order.service';
import { ToastService } from '../../../core/services/toast.service';
import { OrderPage } from '../../../core/models/order.model';

describe('OrderListComponent', () => {
  let orderServiceSpy: jasmine.SpyObj<OrderService>;
  let toastServiceSpy: jasmine.SpyObj<ToastService>;

  const emptyPage: OrderPage = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 };

  function createComponent() {
    TestBed.configureTestingModule({
      imports: [OrderListComponent],
      providers: [
        { provide: OrderService, useValue: orderServiceSpy },
        { provide: ToastService, useValue: toastServiceSpy }
      ]
    });
    return TestBed.createComponent(OrderListComponent).componentInstance;
  }

  beforeEach(() => {
    orderServiceSpy = jasmine.createSpyObj('OrderService', ['getMyOrders']);
    toastServiceSpy = jasmine.createSpyObj('ToastService', ['show']);
    orderServiceSpy.getMyOrders.and.returnValue(of(emptyPage));
  });

  it('ngOnInit loads orders', () => {
    const component = createComponent();

    component.ngOnInit();

    expect(orderServiceSpy.getMyOrders).toHaveBeenCalled();
    expect(component.isLoading).toBeFalse();
  });

  it('load sends the current filters and pagination', () => {
    const component = createComponent();
    component.statusFilter = 'PENDING';
    component.searchTerm = '  phone  ';
    component.page = 2;
    component.size = 5;

    component.load();

    expect(orderServiceSpy.getMyOrders).toHaveBeenCalledWith({
      status: 'PENDING',
      q: 'phone',
      page: 2,
      size: 5
    });
  });

  it('load omits blank filters', () => {
    const component = createComponent();
    component.statusFilter = '';
    component.searchTerm = '   ';

    component.load();

    expect(orderServiceSpy.getMyOrders).toHaveBeenCalledWith({
      status: undefined,
      q: undefined,
      page: 0,
      size: 10
    });
  });

  it('shows a toast when loading fails', () => {
    orderServiceSpy.getMyOrders.and.returnValue(throwError(() => ({})));
    const component = createComponent();

    component.load();

    expect(component.isLoading).toBeFalse();
    expect(toastServiceSpy.show).toHaveBeenCalledWith('Could not load your orders.', 'error');
  });

  it('onSearchSubmit resets to the first page and reloads', () => {
    const component = createComponent();
    component.page = 3;

    component.onSearchSubmit();

    expect(component.page).toBe(0);
    expect(orderServiceSpy.getMyOrders).toHaveBeenCalled();
  });

  it('onStatusChange resets to the first page and reloads', () => {
    const component = createComponent();
    component.page = 3;

    component.onStatusChange();

    expect(component.page).toBe(0);
  });

  it('onPageChange loads the requested page', () => {
    const component = createComponent();

    component.onPageChange(4);

    expect(component.page).toBe(4);
    expect(orderServiceSpy.getMyOrders).toHaveBeenCalled();
  });

  it('trackByOrderId returns the order id', () => {
    const component = createComponent();
    expect(component.trackByOrderId(0, { id: 'order-9' } as never)).toBe('order-9');
  });
});
