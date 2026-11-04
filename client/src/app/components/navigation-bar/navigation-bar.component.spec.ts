import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { DatePipe } from '@angular/common';
import { provideRouter } from '@angular/router';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { NavigationBarComponent } from './navigation-bar.component';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { PermissionService } from '@app/core/services/permission.service';

describe('NavigationBarComponent', () => {
  let fixture: ComponentFixture<NavigationBarComponent>;
  let component: NavigationBarComponent;
  let maintainer: boolean;
  let profile: object | undefined;

  beforeEach(async () => {
    maintainer = false;
    profile = undefined;
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [NavigationBarComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        DatePipe,
        provideQueryClient(new QueryClient()),
        {
          provide: KeycloakService,
          useValue: {
            isLoggedIn: () => !!profile,
            get profile() {
              return profile;
            },
          },
        },
        { provide: PermissionService, useValue: { isAtLeastMaintainer: () => maintainer } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NavigationBarComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('repositoryId', 7);
  });

  it('offers only CI/CD and Environments to anonymous users', () => {
    expect(component.items().map(item => item.label)).toEqual(['CI/CD', 'Environments']);
    expect(component.bottomItems()).toEqual([]);
  });

  it('does not offer repository settings to logged-in users below maintainer', () => {
    profile = { username: 'dev' };
    expect(component.items().map(item => item.label)).toEqual(['CI/CD', 'Environments']);
  });

  it('adds repository settings at the bottom for maintainers', () => {
    profile = { username: 'maintainer' };
    maintainer = true;
    expect(component.items().map(item => item.label)).toEqual(['CI/CD', 'Environments', 'Repository Settings']);
    expect(component.bottomItems().map(item => item.path)).toEqual([['repo', 7, 'settings']]);
  });

  it('never links to removed release or flaky test pages', () => {
    profile = { username: 'maintainer' };
    maintainer = true;
    const labels = component.items().map(item => item.label.toLowerCase());
    const paths = component.items().flatMap(item => item.path.map(String));
    expect(labels.some(label => label.includes('release') || label.includes('flaky'))).toBe(false);
    expect(paths).not.toContain('release');
    expect(paths).not.toContain('flaky-tests');
  });

  it('renders a link per main navigation item', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    const hrefs = Array.from(fixture.nativeElement.querySelectorAll('a')).map(a => (a as HTMLAnchorElement).getAttribute('href'));
    expect(hrefs).toEqual(expect.arrayContaining(['/repo/7/ci-cd', '/repo/7/environment']));
  });
});
