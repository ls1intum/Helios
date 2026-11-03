import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BranchDetailsComponent } from './branch-details.component';
import { importProvidersFrom, signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { TestModule } from '@app/test.module';

describe('Integration Test Branch Details Page', () => {
  let component: BranchDetailsComponent;
  let fixture: ComponentFixture<BranchDetailsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BranchDetailsComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(BranchDetailsComponent);
    component = fixture.componentInstance;

    // Set input properties
    fixture.componentRef.setInput('repositoryId', 1);
    fixture.componentRef.setInput('branchName', 'branch');

    // Mock tanstack query data
    component.query = {
      ...component.query,
      data: signal({ name: 'branch', commitSha: '' }),
    };

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('passes a branch selector to the pipeline and renders no release or test-result sections', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    expect(component.pipelineSelector()).toEqual({ repositoryId: 1, branchName: 'branch' });
    expect(fixture.debugElement.query(By.css('app-pipeline'))).toBeTruthy();
    expect(fixture.debugElement.query(By.css('app-pipeline-test-results'))).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Create Release');
  });
});
