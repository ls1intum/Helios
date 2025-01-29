import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PullRequestDetailsComponent } from './pull-request-details.component';
import { CUSTOM_ELEMENTS_SCHEMA, provideExperimentalZonelessChangeDetection, signal } from '@angular/core';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { By } from '@angular/platform-browser';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MarkdownPipe } from '@app/core/modules/markdown/markdown.pipe';

describe('PullRequestDetailsComponent', () => {
  let component: PullRequestDetailsComponent;
  let fixture: ComponentFixture<PullRequestDetailsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PullRequestDetailsComponent],
      providers: [provideExperimentalZonelessChangeDetection(), provideQueryClient(new QueryClient()), provideNoopAnimations()],
    })
      .overrideComponent(PullRequestDetailsComponent, {
        set: { imports: [MarkdownPipe], schemas: [CUSTOM_ELEMENTS_SCHEMA] },
      })
      .compileComponents();

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

  it('should render pipeline component', async () => {
    // Check if child components get the correct input properties
    const pipelineComponent = fixture.debugElement.query(By.css('app-pipeline'));
    expect(pipelineComponent).toBeTruthy();
    expect(pipelineComponent.properties['selector']).toEqual({ pullRequestId: 1, repositoryId: 1 });
  });
});
