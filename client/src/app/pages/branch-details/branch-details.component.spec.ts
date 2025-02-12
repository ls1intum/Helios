import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BranchDetailsComponent } from './branch-details.component';
import { CUSTOM_ELEMENTS_SCHEMA, provideExperimentalZonelessChangeDetection, signal } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { MarkdownPipe } from '@app/core/modules/markdown/markdown.pipe';

describe('BranchDetailsComponent', () => {
  let component: BranchDetailsComponent;
  let fixture: ComponentFixture<BranchDetailsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BranchDetailsComponent],
      // Todo: figure out how to remove query client provider
      providers: [provideExperimentalZonelessChangeDetection(), provideNoopAnimations(), provideQueryClient(new QueryClient())],
    })
      .overrideComponent(BranchDetailsComponent, {
        set: { imports: [MarkdownPipe], schemas: [CUSTOM_ELEMENTS_SCHEMA] },
      })
      .compileComponents();

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

  it('should render pipeline component', async () => {
    // Check if child components get the correct input properties
    const pipelineComponent = fixture.debugElement.query(By.css('app-pipeline'));
    expect(pipelineComponent).toBeTruthy();
    expect(pipelineComponent.properties['selector']).toEqual({ branchName: 'branch', repositoryId: 1 });
  });
});
