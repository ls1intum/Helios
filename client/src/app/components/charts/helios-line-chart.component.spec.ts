import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { HeliosLineChartComponent, type ChartSeries } from './helios-line-chart.component';
import { ThemeService } from '@app/core/services/theme.service';

describe('HeliosLineChartComponent', () => {
  let fixture: ComponentFixture<HeliosLineChartComponent>;
  let themeService: ThemeService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HeliosLineChartComponent],
      providers: [provideZonelessChangeDetection(), provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(HeliosLineChartComponent);
    themeService = TestBed.inject(ThemeService);
  });

  function setSeries(series: ChartSeries[]) {
    fixture.componentRef.setInput('series', series);
    fixture.detectChanges();
  }

  it('builds one Chart.js dataset per series with the requested label', () => {
    setSeries([
      { label: 'queue p50', data: [{ x: '2026-05-18T10:00:00Z', y: 30 }] },
      { label: 'queue p95', data: [{ x: '2026-05-18T10:00:00Z', y: 120 }] },
    ]);
    const data = fixture.componentInstance.chartData();
    expect(data.datasets).toHaveLength(2);
    expect(data.datasets[0].label).toBe('queue p50');
    expect(data.datasets[1].label).toBe('queue p95');
  });

  it('rebuilds options when dark mode toggles', () => {
    setSeries([{ label: 'foo', data: [] }]);
    themeService.isDarkMode.set(false);
    const lightOptions = JSON.stringify(fixture.componentInstance.chartOptions());

    themeService.isDarkMode.set(true);
    fixture.detectChanges();
    const darkOptions = JSON.stringify(fixture.componentInstance.chartOptions());

    expect(darkOptions).not.toBe(lightOptions);
  });

  it('uses different palette colours across datasets', () => {
    setSeries([
      { label: 'a', data: [] },
      { label: 'b', data: [] },
      { label: 'c', data: [] },
    ]);
    const datasets = fixture.componentInstance.chartData().datasets;
    const colours = new Set(datasets.map(d => d.borderColor));
    expect(colours.size).toBeGreaterThan(1);
  });
});
