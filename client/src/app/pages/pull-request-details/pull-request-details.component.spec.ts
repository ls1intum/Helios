import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PullRequestDetailsComponent } from './pull-request-details.component';
import { importProvidersFrom, signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { TestModule } from '@app/test.module';

describe('Integration Test Pull Request Details Page', () => {
  let component: PullRequestDetailsComponent;
  let fixture: ComponentFixture<PullRequestDetailsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PullRequestDetailsComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(PullRequestDetailsComponent);
    component = fixture.componentInstance;

    // Set input properties
    fixture.componentRef.setInput('repositoryId', 1);
    fixture.componentRef.setInput('pullRequestNumber', 1);

    // Mock tanstack query data
    component.query = {
      ...component.query,
      data: signal({
        id: 1,
        title: 'title',
        number: 1,
        state: 'OPEN',
        isDraft: false,
        isMerged: false,
        commentsCount: 0,
        additions: 0,
        deletions: 0,
        headSha: 'sha',
        baseSha: 'sha',
        headRefName: 'headRefName',
        headRefRepoNameWithOwner: 'headRefRepoNameWithOwner',
        htmlUrl: 'http://example.com',
      }),
    };

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('passes a pull request selector to the pipeline and renders no test-result section', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    expect(component.pipelineSelector()).toEqual({ repositoryId: 1, pullRequestId: 1 });
    expect(fixture.debugElement.query(By.css('app-pipeline'))).toBeTruthy();
    expect(fixture.debugElement.query(By.css('app-pipeline-test-results'))).toBeNull();
  });
});
