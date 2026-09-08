import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { OrderService } from './order.service';
import { OrderPage } from '../models/order.model';

describe('OrderService', () => {
  let service: OrderService;
  let httpMock: HttpTestingController;

  const samplePage: OrderPage = {
    content: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [OrderService]
    });
    service = TestBed.inject(OrderService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('getMyOrders() sends status/q/page/size as query params', () => {
    service.getMyOrders({ status: 'PENDING', q: 'phone', page: 1, size: 5 }).subscribe((page) => {
      expect(page).toEqual(samplePage);
    });

    const req = httpMock.expectOne(
      (r) => r.url === '/api/orders' && r.params.get('status') === 'PENDING' && r.params.get('q') === 'phone'
        && r.params.get('page') === '1' && r.params.get('size') === '5'
    );
    expect(req.request.method).toBe('GET');
    req.flush(samplePage);
  });

  it('getMyOrders() omits unset filters', () => {
    service.getMyOrders().subscribe();

    const req = httpMock.expectOne('/api/orders');
    expect(req.request.params.keys()).toHaveSize(0);
    req.flush(samplePage);
  });

  it('cancelOrder() sends a PATCH to /orders/{id}/cancel', () => {
    service.cancelOrder('order-1').subscribe();

    const req = httpMock.expectOne('/api/orders/order-1/cancel');
    expect(req.request.method).toBe('PATCH');
    req.flush({});
  });

  it('updateOrderStatus() sends the target status in the body', () => {
    service.updateOrderStatus('order-1', 'SHIPPED').subscribe();

    const req = httpMock.expectOne('/api/orders/order-1/status');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'SHIPPED' });
    req.flush({});
  });
});
