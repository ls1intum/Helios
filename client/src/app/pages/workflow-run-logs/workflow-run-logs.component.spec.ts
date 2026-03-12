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
});
