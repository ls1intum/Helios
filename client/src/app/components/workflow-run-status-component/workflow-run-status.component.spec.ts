import { ComponentFixture, TestBed } from '@angular/core/testing';

import { WorkflowRunStatusComponent } from './workflow-run-status.component';
import { provideExperimentalZonelessChangeDetection } from '@angular/core';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

describe('WorkflowRunStatusComponent', () => {
  let component: WorkflowRunStatusComponent;
  let fixture: ComponentFixture<WorkflowRunStatusComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkflowRunStatusComponent],
      providers: [provideExperimentalZonelessChangeDetection(), provideQueryClient(new QueryClient()), provideNoopAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(WorkflowRunStatusComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
