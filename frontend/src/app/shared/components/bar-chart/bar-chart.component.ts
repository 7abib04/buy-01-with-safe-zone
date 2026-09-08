import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface BarChartDatum {
  label: string;
  value: number;
}

@Component({
  selector: 'app-bar-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './bar-chart.component.html',
  styleUrl: './bar-chart.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BarChartComponent {
  @Input() data: BarChartDatum[] = [];
  @Input() emptyMessage = 'No data yet.';
  @Input() valuePrefix = '';
  @Input() valueSuffix = '';

  get maxValue(): number {
    return Math.max(1, ...this.data.map((d) => d.value));
  }

  barWidth(value: number): number {
    if (this.maxValue === 0) {
      return 0;
    }
    const minVisibleWidth = value > 0 ? 4 : 0;
    return Math.max((value / this.maxValue) * 100, minVisibleWidth);
  }
}
