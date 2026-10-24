import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { EnvironmentReviewersDto } from '@app/core/modules/openapi';
import { getEnvironmentReviewersQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { EnvironmentReviewersComponent } from './environment-reviewers.component';

describe('EnvironmentReviewersComponent', () => {
  let fixture: ComponentFixture<EnvironmentReviewersComponent>;
  let queryClient: QueryClient;

  beforeEach(async () => {
    queryClient = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, retry: false } } });
    await TestBed.configureTestingModule({
      imports: [EnvironmentReviewersComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideNoopAnimations(),
        provideQueryClient(queryClient),
        { provide: KeycloakService, useValue: { getPreferredUsername: () => 'alice' } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(EnvironmentReviewersComponent);
  });

  afterEach(() => queryClient.clear());

  async function render(data: EnvironmentReviewersDto, environmentId = 7) {
    queryClient.setQueryData(getEnvironmentReviewersQueryKey({ path: { environmentId } }), data);
    fixture.componentRef.setInput('environmentId', environmentId);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it.each([{}, { reviewers: [] }])('does not show a reviewer warning when there are no reviewers: %s', async data => {
    const host = await render(data);
    expect(host.querySelector('i-tabler')).toBeNull();
    expect(document.querySelector('[role="tooltip"]')).toBeNull();
  });

  it('shows user and team reviewer names in the real tooltip', async () => {
    const host = await render({
      reviewers: [
        { id: 1, login: 'alice' },
        { id: 2, name: 'Release managers', login: 'release-team' },
      ],
    });
    host.querySelector('span')!.dispatchEvent(new MouseEvent('mouseenter'));
    await vi.waitFor(() => expect(document.querySelector('[role="tooltip"]')?.textContent).toContain('Required Reviewers:'));
    const tooltip = document.querySelector('[role="tooltip"]')!;
    expect(tooltip.textContent).toContain('alice');
    expect(tooltip.textContent).toContain('Release managers');
    expect(tooltip.textContent).not.toContain('release-team');
    expect(tooltip.textContent).not.toContain('No Self-approval');
    expect(tooltip.querySelector('i-tabler[name="users-group"]')).toBeTruthy();
    expect(tooltip.querySelector('i-tabler[name="user"]')).toBeTruthy();
  });

  it('includes the self-approval restriction when required by the environment', async () => {
    const host = await render({ reviewers: [{ id: 1, login: 'alice' }], preventSelfReview: true });
    host.querySelector('span')!.dispatchEvent(new MouseEvent('mouseenter'));
    await vi.waitFor(() => expect(document.querySelector('[role="tooltip"]')?.textContent).toContain('No Self-approval'));
  });

  it('removes the warning when the required reviewers are cleared', async () => {
    const host = await render({ reviewers: [{ id: 1, login: 'alice' }] });
    expect(host.querySelector('i-tabler')).toBeTruthy();
    queryClient.setQueryData(getEnvironmentReviewersQueryKey({ path: { environmentId: 7 } }), { reviewers: [] });
    await vi.waitFor(() => expect(host.querySelector('i-tabler')).toBeNull());
  });
});
