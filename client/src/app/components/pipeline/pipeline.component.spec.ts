import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PipelineComponent } from './pipeline.component';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

describe('PipelineComponent', () => {
  let component: PipelineComponent;
  let fixture: ComponentFixture<PipelineComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PipelineComponent],
      providers: [provideZonelessChangeDetection(), provideQueryClient(new QueryClient()), provideNoopAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(PipelineComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
