import { PaginationComponent } from './pagination.component';

describe('PaginationComponent', () => {
  let component: PaginationComponent;

  beforeEach(() => {
    component = new PaginationComponent();
  });

  it('hasPrevious is false on the first page', () => {
    component.page = 0;
    component.totalPages = 3;
    expect(component.hasPrevious).toBeFalse();
  });

  it('hasPrevious is true after the first page', () => {
    component.page = 1;
    component.totalPages = 3;
    expect(component.hasPrevious).toBeTrue();
  });

  it('hasNext is true when more pages remain', () => {
    component.page = 0;
    component.totalPages = 3;
    expect(component.hasNext).toBeTrue();
  });

  it('hasNext is false on the last page', () => {
    component.page = 2;
    component.totalPages = 3;
    expect(component.hasNext).toBeFalse();
  });

  it('previous() emits page - 1 when possible', () => {
    component.page = 2;
    component.totalPages = 3;
    const emitted: number[] = [];
    component.pageChange.subscribe((p) => emitted.push(p));

    component.previous();

    expect(emitted).toEqual([1]);
  });

  it('previous() does nothing on the first page', () => {
    component.page = 0;
    component.totalPages = 3;
    const emitted: number[] = [];
    component.pageChange.subscribe((p) => emitted.push(p));

    component.previous();

    expect(emitted).toEqual([]);
  });

  it('next() emits page + 1 when possible', () => {
    component.page = 0;
    component.totalPages = 3;
    const emitted: number[] = [];
    component.pageChange.subscribe((p) => emitted.push(p));

    component.next();

    expect(emitted).toEqual([1]);
  });

  it('next() does nothing on the last page', () => {
    component.page = 2;
    component.totalPages = 3;
    const emitted: number[] = [];
    component.pageChange.subscribe((p) => emitted.push(p));

    component.next();

    expect(emitted).toEqual([]);
  });
});
