import { ComponentFixture, TestBed } from '@angular/core/testing';

import { WorkflowRunStatusComponent } from './workflow-run-status.component';

describe('WorkflowRunStatusComponent', () => {
  let component: WorkflowRunStatusComponent;
  let fixture: ComponentFixture<WorkflowRunStatusComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkflowRunStatusComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(WorkflowRunStatusComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
