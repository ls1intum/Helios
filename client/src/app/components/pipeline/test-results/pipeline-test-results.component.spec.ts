import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';

import { PipelineTestResultsComponent } from './pipeline-test-results.component';
import { PermissionService } from '@app/core/services/permission.service';
import type { TestCaseDto } from '@app/core/modules/openapi';

describe('PipelineTestResultsComponent', () => {
  let component: PipelineTestResultsComponent;
  let fixture: ComponentFixture<PipelineTestResultsComponent>;

  const permissionServiceMock = {
    hasWritePermission: () => true,
  };

  const failedTestCase: TestCaseDto = {
    id: 1,
    name: 'should fail',
    className: 'com.example.FailingTest',
    status: 'FAILED',
    time: 1,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PipelineTestResultsComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideNoopAnimations(),
        provideQueryClient(new QueryClient()),
        { provide: PermissionService, useValue: permissionServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PipelineTestResultsComponent);
    component = fixture.componentInstance;
    Object.defineProperty(component, 'repositoryId', {
      configurable: true,
      value: () => 1,
    });
  });

  it('allows AI analysis for failed tests when the user has write access', () => {
    permissionServiceMock.hasWritePermission = () => true;

    expect(component.canAnalyzeTestFailureWithAi(failedTestCase)).toBe(true);
  });

  it('hides AI analysis for failed tests when the user lacks write access', () => {
    permissionServiceMock.hasWritePermission = () => false;

    expect(component.canAnalyzeTestFailureWithAi(failedTestCase)).toBe(false);
  });
});
