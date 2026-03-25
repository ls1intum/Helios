import { ComponentFixture, TestBed } from '@angular/core/testing';
import { importProvidersFrom, signal } from '@angular/core';
import { ReleaseCandidateDetailsComponent } from './release-candidate-details.component';
import { PermissionService } from '@app/core/services/permission.service';
import { TestModule } from '@app/test.module';
import { ReleaseInfoDetailsDto } from '@app/core/modules/openapi';

describe('Integration Test Release Candidate Details Page', () => {
  let component: ReleaseCandidateDetailsComponent;
  let fixture: ComponentFixture<ReleaseCandidateDetailsComponent>;

  const releaseCandidate: ReleaseInfoDetailsDto = {
    name: 'test',
    commit: {
      sha: 'abcdef1234567',
    },
    deployments: [],
    evaluations: [],
    createdAt: '2026-03-09T10:00:00Z',
    createdBy: {
      id: 1,
      login: 'candidate-owner',
      avatarUrl: 'https://example.com/candidate.png',
      name: 'Candidate Owner',
      htmlUrl: 'https://github.com/candidate-owner',
    },
    release: {
      isDraft: true,
      isPrerelease: false,
      body: 'Release notes',
      githubUrl: 'https://github.com/ls1intum/Helios/releases/tag/test',
      creator: {
        id: 2,
        login: 'release-owner',
        avatarUrl: 'https://example.com/release.png',
        name: 'Release Owner',
        htmlUrl: 'https://github.com/release-owner',
      },
    },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReleaseCandidateDetailsComponent],
      providers: [
        importProvidersFrom(TestModule),
        {
          provide: PermissionService,
          useValue: {
            isAtLeastMaintainer: signal(false),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ReleaseCandidateDetailsComponent);
    component = fixture.componentInstance;

    // Set input properties
    fixture.componentRef.setInput('name', 'test');
    component.releaseCandidateQuery = {
      ...component.releaseCandidateQuery,
      data: signal(releaseCandidate),
      isPending: signal(false),
      isError: signal(false),
    } as typeof component.releaseCandidateQuery;

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders the published release creator separately from the release candidate creator', () => {
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent.replace(/\s+/g, ' ');

    expect(text).toContain('created by');
    expect(text).toContain('candidate-owner');
    expect(text).toContain('release created by');
    expect(text).toContain('release-owner');
  });
});
