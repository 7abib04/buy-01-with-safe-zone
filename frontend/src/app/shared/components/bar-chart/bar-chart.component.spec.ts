import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BarChartComponent } from './bar-chart.component';

describe('BarChartComponent', () => {
  let component: BarChartComponent;
  let fixture: ComponentFixture<BarChartComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BarChartComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(BarChartComponent);
    component = fixture.componentInstance;
  });

  it('creates', () => {
    expect(component).toBeTruthy();
  });

  it('barWidth() scales relative to the largest value', () => {
    component.data = [
      { label: 'A', value: 10 },
      { label: 'B', value: 5 }
    ];

    expect(component.barWidth(10)).toBe(100);
    expect(component.barWidth(5)).toBe(50);
  });

  it('barWidth() gives a minimum visible width to non-zero values', () => {
    component.data = [
      { label: 'A', value: 1000 },
      { label: 'B', value: 1 }
    ];

    expect(component.barWidth(1)).toBeGreaterThanOrEqual(4);
  });

  it('barWidth() returns 0 for a zero value', () => {
    component.data = [{ label: 'A', value: 0 }];

    expect(component.barWidth(0)).toBe(0);
  });
});
