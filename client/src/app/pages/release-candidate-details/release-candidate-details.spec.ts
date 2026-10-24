import { ComponentFixture, TestBed } from '@angular/core/testing';
import { importProvidersFrom, signal } from '@angular/core';
import { ReleaseCandidateDetailsComponent } from './release-candidate-details.component';
import { PermissionService } from '@app/core/services/permission.service';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { TestModule } from '@app/test.module';
import { ReleaseCandidateEvaluationDto, ReleaseInfoDetailsDto } from '@app/core/modules/openapi';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { ConfirmationService, MessageService } from 'primeng/api';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
import { ReleaseEvaluationDialogComponent, ReleaseEvaluationDialogResult } from '@app/components/dialogs/release-evaluation-dialog/release-evaluation-dialog.component';

describe('Release candidate details', () => {
  let component: ReleaseCandidateDetailsComponent;
  let fixture: ComponentFixture<ReleaseCandidateDetailsComponent>;
  let candidate: ReturnType<typeof signal<ReleaseInfoDetailsDto | undefined>>;
  let maintainer: ReturnType<typeof signal<boolean>>;
  let messages: MessageService;
  let confirmation: ConfirmationService;
  let dialog: DialogService;

  const createCandidate = (overrides: Partial<ReleaseInfoDetailsDto> = {}): ReleaseInfoDetailsDto => ({
    name: 'v2.0-rc1',
    commit: { sha: 'abcdef1234567' },
    deployments: [],
    evaluations: [],
    createdAt: '2026-03-09T10:00:00Z',
    createdBy: { id: 1, login: 'candidate-owner', avatarUrl: 'https://example.com/candidate.png', name: 'Candidate Owner', htmlUrl: 'https://github.com/candidate-owner' },
    ...overrides,
  });
  const publishedRelease = {
    isDraft: true,
    isPrerelease: true,
    body: 'Published release notes',
    githubUrl: 'https://github.com/ls1intum/Helios/releases/tag/v2.0-rc1',
    creator: { id: 2, login: 'release-owner', avatarUrl: 'https://example.com/release.png', name: 'Release Owner', htmlUrl: 'https://github.com/release-owner' },
  };
  const evaluation = (isWorking: boolean, comment?: string): ReleaseCandidateEvaluationDto =>
    ({
      isWorking,
      comment,
      user: { id: 1, login: 'CANDIDATE-OWNER', avatarUrl: 'https://example.com/candidate.png' },
    }) as ReleaseCandidateEvaluationDto;
  const text = () => fixture.nativeElement.textContent.replace(/\s+/g, ' ');
  const click = (label: string) => {
    const button = Array.from(fixture.nativeElement.querySelectorAll('button')).find(element => (element as HTMLElement).textContent?.trim() === label) as
      HTMLButtonElement | undefined;
    expect(button, `button ${label}`).toBeDefined();
    button!.click();
    fixture.detectChanges();
  };

  beforeEach(async () => {
    maintainer = signal(false);
    candidate = signal(createCandidate());
    await TestBed.configureTestingModule({
      imports: [ReleaseCandidateDetailsComponent],
      providers: [
        importProvidersFrom(TestModule),
        provideQueryClient(new QueryClient({ defaultOptions: { queries: { enabled: false, retry: false } } })),
        { provide: PermissionService, useValue: { isAtLeastMaintainer: maintainer, isAdmin: signal(false) } },
        { provide: KeycloakService, useValue: { getPreferredUsername: () => 'candidate-owner', isLoggedIn: () => false } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ReleaseCandidateDetailsComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('name', 'v2.0-rc1');
    component.releaseCandidateQuery = {
      ...component.releaseCandidateQuery,
      data: candidate,
      isPending: signal(false),
      isError: signal(false),
    } as unknown as typeof component.releaseCandidateQuery;
    messages = TestBed.inject(MessageService);
    confirmation = TestBed.inject(ConfirmationService);
    dialog = fixture.debugElement.injector.get(DialogService);
    fixture.detectChanges();
    await fixture.whenStable();
  });
  afterEach(() => vi.restoreAllMocks());

  it('shows loading placeholders while candidate data is unavailable', () => {
    candidate.set(undefined);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('p-skeleton')).toHaveLength(2);
    expect(text()).not.toContain('Release Notes');
  });
  it('renders the candidate, abbreviated commit, and empty review and notes states', () => {
    expect(text()).toContain('v2.0-rc1');
    expect(text()).toContain('abcdef1');
    expect(text()).toContain('No reviews yet');
    expect(text()).toContain('No release notes available.');
    expect(text()).not.toContain('Generate Release Notes');
  });
  it('hides management and evaluation actions without maintainer permission', () => {
    expect(text()).not.toContain('Publish to GitHub');
    expect(text()).not.toContain('Working');
    expect(text()).not.toContain('Broken');
    expect(component.canEditReleaseNotes()).toBe(false);
  });
  it('offers publishing, evaluation, and note editing to maintainers', () => {
    maintainer.set(true);
    fixture.detectChanges();
    expect(text()).toContain('Publish to GitHub');
    expect(text()).toContain('Working');
    expect(text()).toContain('Broken');
    expect(text()).toContain('Generate Release Notes');
    expect(component.canEditReleaseNotes()).toBe(true);
  });
  it('renders published metadata and its creator separately from the candidate creator', () => {
    candidate.set(createCandidate({ body: 'Candidate notes', release: publishedRelease }));
    maintainer.set(true);
    fixture.detectChanges();
    expect(text()).toContain('created by candidate-owner');
    expect(text()).toContain('release created by release-owner');
    expect(text()).toContain('Published to GitHub');
    expect(text()).toContain('Prerelease');
    expect(text()).toContain('Draft');
    expect(text()).toContain('Open on GitHub');
    expect(text()).not.toContain('Publish to GitHub');
    expect(text()).not.toContain('Generate Release Notes');
    expect(component.releaseNotes()).toBe('Published release notes');
    expect(component.canEditReleaseNotes()).toBe(false);
  });
  it('uses candidate notes when the GitHub release has no body', () => {
    candidate.set(createCandidate({ body: 'Candidate fallback', release: { ...publishedRelease, body: '' } }));
    fixture.detectChanges();
    expect(text()).toContain('Candidate fallback');
  });
  it('identifies missing creators as unknown', () => {
    candidate.set(createCandidate({ createdBy: undefined }));
    fixture.detectChanges();
    expect(text()).toContain('created by Unknown');
  });
  it('renders reviews and marks the current evaluation ignoring login casing', () => {
    candidate.set(createCandidate({ evaluations: [evaluation(true, 'Smoke tests passed')] }));
    maintainer.set(true);
    fixture.detectChanges();
    expect(text()).toContain('Reviews');
    expect(text()).not.toContain('No reviews yet');
    expect(fixture.nativeElement.querySelector('p-button[label="Working"]').classList.contains('opacity-50')).toBe(true);
    expect(component.hasUserEvaluatedTo(false)).toBe(false);
  });
  it('does not mark another user evaluation or missing data as the current user evaluation', () => {
    candidate.set(createCandidate({ evaluations: [{ ...evaluation(false), user: { ...evaluation(false).user, login: 'other-user' } }] }));
    expect(component.hasUserEvaluatedTo(false)).toBe(false);
    candidate.set(undefined);
    expect(component.hasUserEvaluatedTo(true)).toBe(false);
  });
  it.each([
    [true, undefined, 'Marked as working'],
    [false, '   ', 'Marked as broken'],
    [false, 'Deployment failed', 'Marked as broken\n\nComment: Deployment failed'],
  ])('describes working status %s with comment %s', (isWorking, comment, expected) => {
    expect(component.getEvaluationTooltip(evaluation(isWorking, comment))).toBe(expected);
  });
  it('opens existing notes in the editor and restores them when cancelled', () => {
    maintainer.set(true);
    candidate.set(createCandidate({ body: 'Original notes' }));
    fixture.detectChanges();
    click('Edit');
    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    expect(textarea.value).toBe('Original notes');
    textarea.value = 'Unsaved edits';
    textarea.dispatchEvent(new Event('input'));
    click('Cancel');
    expect(fixture.nativeElement.querySelector('textarea')).toBeNull();
    expect(component.releaseNotesForm.controls.releaseNotes.value).toBe('Original notes');
    expect(text()).toContain('Original notes');
  });
  it('submits edited release notes through Save', () => {
    maintainer.set(true);
    const mutate = vi.spyOn(component.updateReleaseNotesMutation, 'mutate').mockImplementation(() => {});
    fixture.detectChanges();
    click('Edit');
    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = '## Changes\nFix deployment';
    textarea.dispatchEvent(new Event('input'));
    click('Save');
    expect(mutate).toHaveBeenCalledExactlyOnceWith({ body: { name: 'v2.0-rc1', notes: '## Changes\nFix deployment' } });
  });
  it('requests generated notes for the current candidate', () => {
    maintainer.set(true);
    const mutate = vi.spyOn(component.generateReleaseNotesMutation, 'mutate').mockImplementation(() => {});
    fixture.detectChanges();
    click('Generate Release Notes');
    expect(mutate).toHaveBeenCalledExactlyOnceWith({ body: { name: 'v2.0-rc1' } });
  });
  it('prevents release name and note changes once published', () => {
    candidate.set(createCandidate({ release: publishedRelease }));
    const notes = vi.spyOn(component.updateReleaseNotesMutation, 'mutate').mockImplementation(() => {});
    const generate = vi.spyOn(component.generateReleaseNotesMutation, 'mutate').mockImplementation(() => {});
    const name = vi.spyOn(component.updateReleaseNameMutation, 'mutate').mockImplementation(() => {});
    component.editReleaseNotes();
    component.saveReleaseNotes();
    component.generateReleaseNotes();
    component.editName();
    component.saveName();
    expect(component.isEditingReleaseNotes()).toBe(false);
    expect(component.isEditingName()).toBe(false);
    expect(notes).not.toHaveBeenCalled();
    expect(generate).not.toHaveBeenCalled();
    expect(name).not.toHaveBeenCalled();
  });
  it('initializes the name editor and cancels without saving', () => {
    component.editName();
    fixture.detectChanges();
    expect((fixture.nativeElement.querySelector('input[formControlName="releaseName"]') as HTMLInputElement).value).toBe('v2.0-rc1');
    component.cancelEditingName();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input[formControlName="releaseName"]')).toBeNull();
  });
  it('rejects whitespace-only names with an error', () => {
    const mutate = vi.spyOn(component.updateReleaseNameMutation, 'mutate').mockImplementation(() => {});
    const add = vi.spyOn(messages, 'add');
    component.editName();
    component.releaseNameForm.controls.releaseName.setValue('   ');
    component.saveName();
    expect(mutate).not.toHaveBeenCalled();
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error', detail: 'Release name cannot be empty' }));
    expect(component.isEditingName()).toBe(true);
  });
  it('sends old and new names when renaming', () => {
    const mutate = vi.spyOn(component.updateReleaseNameMutation, 'mutate').mockImplementation(() => {});
    component.editName();
    component.releaseNameForm.controls.releaseName.setValue('v2.0-rc2');
    component.saveName();
    expect(mutate).toHaveBeenCalledExactlyOnceWith({ body: { oldName: 'v2.0-rc1', newName: 'v2.0-rc2' } });
  });
  it('waits for confirmation before deleting', () => {
    const confirm = vi.spyOn(confirmation, 'confirm');
    const mutate = vi.spyOn(component.deleteReleaseCandidateMutation, 'mutate').mockImplementation(() => {});
    component.deleteReleaseCandidate(candidate()!);
    expect(confirm).toHaveBeenCalledWith(expect.objectContaining({ header: 'Delete Release Candidate', message: expect.stringContaining('v2.0-rc1') }));
    expect(mutate).not.toHaveBeenCalled();
    confirm.mock.calls[0][0].accept!();
    expect(mutate).toHaveBeenCalledExactlyOnceWith({ body: { name: 'v2.0-rc1' } });
  });
  it('opens publish confirmation from the template and publishes only after approval', () => {
    maintainer.set(true);
    const mutate = vi.spyOn(component.publishReleaseDraftMutation, 'mutate').mockImplementation(() => {});
    fixture.detectChanges();
    click('Publish to GitHub');
    expect(component.publishDialogVisible()).toBe(true);
    expect(component.releaseName()).toBe('v2.0-rc1');
    expect(mutate).not.toHaveBeenCalled();
    component.onPublishReleaseDraftConfirmed(false);
    expect(mutate).not.toHaveBeenCalled();
    component.onPublishReleaseDraftConfirmed(true);
    expect(mutate).toHaveBeenCalledExactlyOnceWith({ body: { name: 'v2.0-rc1' } });
    expect(component.releaseName()).toBeUndefined();
  });
  it('does not publish without a selected candidate or if it disappears during confirmation', () => {
    const mutate = vi.spyOn(component.publishReleaseDraftMutation, 'mutate').mockImplementation(() => {});
    component.onPublishReleaseDraftConfirmed(true);
    candidate.set(undefined);
    component.publishReleaseDraft();
    expect(component.publishDialogVisible()).toBe(false);
    component.releaseName.set('v2.0-rc1');
    component.onPublishReleaseDraftConfirmed(true);
    expect(mutate).not.toHaveBeenCalled();
  });
  it('opens the published GitHub URL and ignores unpublished or missing candidates', () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    component.openReleaseInGitHub();
    expect(open).not.toHaveBeenCalled();
    candidate.set(createCandidate({ release: publishedRelease }));
    component.openReleaseInGitHub();
    expect(open).toHaveBeenCalledExactlyOnceWith(publishedRelease.githubUrl, '_blank');
    candidate.set(undefined);
    component.openReleaseInGitHub();
    expect(open).toHaveBeenCalledTimes(1);
  });
  it.each([true, false])('reuses comments only for the same evaluation status (%s)', isWorking => {
    candidate.set(createCandidate({ evaluations: [evaluation(true, 'Existing comment')] }));
    const open = vi.spyOn(dialog, 'open').mockReturnValue(null);
    component.evaluateReleaseCandidate(isWorking);
    expect(open).toHaveBeenCalledWith(ReleaseEvaluationDialogComponent, {
      width: '500px',
      data: { releaseName: 'v2.0-rc1', isWorking, comment: isWorking ? 'Existing comment' : '' },
    });
  });
  it('submits dialog results and ignores cancellation', () => {
    const closed = new Subject<ReleaseEvaluationDialogResult | undefined>();
    vi.spyOn(dialog, 'open').mockReturnValue({ onClose: closed } as unknown as DynamicDialogRef);
    const mutate = vi.spyOn(component.evaluateReleaseCandidateMutation, 'mutate').mockImplementation(() => {});
    component.evaluateReleaseCandidate(false);
    closed.next(undefined);
    expect(mutate).not.toHaveBeenCalled();
    closed.next({ isWorking: false, comment: 'Health check failed' });
    expect(mutate).toHaveBeenCalledExactlyOnceWith({ body: { name: 'v2.0-rc1', isWorking: false, comment: 'Health check failed' } });
    closed.complete();
  });
  it('does not open evaluation before candidate data is available', () => {
    const open = vi.spyOn(dialog, 'open').mockReturnValue(null);
    candidate.set(undefined);
    component.evaluateReleaseCandidate(true);
    expect(open).not.toHaveBeenCalled();
  });
});
