import { ComponentFixture, TestBed } from '@angular/core/testing';
import { importProvidersFrom } from '@angular/core';

import { WorkflowRunListComponent } from './workflow-run-list.component';
import { TestModule } from '@app/test.module';

describe('Integration Test Workflow Run List Page', () => {
  let component: WorkflowRunListComponent;
  let fixture: ComponentFixture<WorkflowRunListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkflowRunListComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(WorkflowRunListComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('repositoryId', 1);

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders the workflow runs table', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    const hostElement: HTMLElement = fixture.nativeElement;
    const tableElement = hostElement.querySelector('app-workflow-runs-table');

    expect(tableElement).toBeTruthy();
  });
});
