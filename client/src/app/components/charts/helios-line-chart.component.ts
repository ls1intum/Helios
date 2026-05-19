import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { ChartModule } from 'primeng/chart';
import { ThemeService } from '@app/core/services/theme.service';
// Required side-effect import: registers the date adapter used by `type: 'time'` scales below.
// Without it Chart.js throws at render time ("complete date adapter is provided").
import 'chartjs-adapter-date-fns';

export interface ChartSeries {
  label: string;
  data: { x: string | number | Date; y: number }[];
}

/**
 * Thin PrimeNG `<p-chart>` wrapper that rebuilds its Chart.js options from the current PrimeNG
 * theme tokens. Reacts to dark-mode toggles via `ThemeService.isDarkMode`.
 */
@Component({
  selector: 'app-helios-line-chart',
  standalone: true,
  imports: [ChartModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p-chart type="line" [data]="chartData()" [options]="chartOptions()" height="320"></p-chart>`,
})
export class HeliosLineChartComponent {
  series = input.required<ChartSeries[]>();
  yAxisLabel = input<string>('');

  private themeService = inject(ThemeService);

  constructor() {
    effect(() => {
      // Touch the signal so the computed re-fires when dark mode flips.
      this.themeService.isDarkMode();
    });
  }

  chartData = computed(() => {
    const palette = this.palette();
    return {
      datasets: this.series().map((s, i) => ({
        label: s.label,
        data: s.data,
        borderColor: palette[i % palette.length],
        backgroundColor: palette[i % palette.length] + '20',
        tension: 0.2,
        fill: false,
      })),
    };
  });

  chartOptions = computed(() => {
    const isDark = this.themeService.isDarkMode();
    const textColor = isDark ? '#e5e7eb' : '#111827';
    const gridColor = isDark ? '#374151' : '#e5e7eb';
    return {
      maintainAspectRatio: false,
      responsive: true,
      plugins: {
        legend: {
          labels: { color: textColor },
        },
      },
      scales: {
        x: {
          type: 'time',
          time: { unit: 'hour' },
          ticks: { color: textColor },
          grid: { color: gridColor },
        },
        y: {
          title: { display: !!this.yAxisLabel(), text: this.yAxisLabel(), color: textColor },
          ticks: { color: textColor },
          grid: { color: gridColor },
          beginAtZero: true,
        },
      },
    };
  });

  private palette(): string[] {
    return ['#2563eb', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6'];
  }
}
