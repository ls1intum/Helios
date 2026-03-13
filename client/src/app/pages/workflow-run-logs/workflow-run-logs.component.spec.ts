import { ComponentFixture, TestBed } from '@angular/core/testing';
import { importProvidersFrom } from '@angular/core';
import { TestModule } from '@app/test.module';
import { WorkflowRunLogsComponent } from './workflow-run-logs.component';

describe('Integration Test Workflow Run Logs Page', () => {
  let component: WorkflowRunLogsComponent;
  let fixture: ComponentFixture<WorkflowRunLogsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkflowRunLogsComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(WorkflowRunLogsComponent);
    component = fixture.componentInstance;

    fixture.componentRef.setInput('repositoryId', 1);
    fixture.componentRef.setInput('workflowRunId', 42);

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should return a success icon for successful workflow runs', () => {
    expect(component.getWorkflowRunOutcome('SUCCESS')).toMatchObject({
      icon: 'circle-check',
      label: 'Success',
    });
  });

  it('should return a failure icon for failed workflow runs', () => {
    expect(component.getWorkflowRunOutcome('FAILURE')).toMatchObject({
      icon: 'circle-x',
      label: 'Failure',
    });
  });

  it('should return a cancelled icon for cancelled workflow runs', () => {
    expect(component.getWorkflowRunOutcome('CANCELLED')).toMatchObject({
      icon: 'circle-minus',
      label: 'Cancelled',
    });
  });
});
