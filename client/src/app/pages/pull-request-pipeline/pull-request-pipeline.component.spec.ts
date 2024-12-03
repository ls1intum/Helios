import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PullRequestPipelineComponent } from './pull-request-pipeline.component';

describe('PipelineComponent', () => {
  let component: PullRequestPipelineComponent;
  let fixture: ComponentFixture<PullRequestPipelineComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PullRequestPipelineComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(PullRequestPipelineComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
