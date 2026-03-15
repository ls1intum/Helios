import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RepositoryOverviewComponent } from './repository-overview.component';
import { importProvidersFrom } from '@angular/core';
import { TestModule } from '@app/test.module';
import { By } from '@angular/platform-browser';
import { RepositoryInfoDto } from '@app/core/modules/openapi';
import { getAllRepositoriesQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { DataView } from 'primeng/dataview';

describe('Integration Test Repository Overview Page', () => {
  let component: RepositoryOverviewComponent;
  let fixture: ComponentFixture<RepositoryOverviewComponent> | undefined;
  let queryClient: QueryClient;

  const repositories: RepositoryInfoDto[] = Array.from({ length: 20 }, (_, index) => ({
    id: index + 1,
    name: `repo-${index + 1}`,
    nameWithOwner: `ls1intum/repo-${index + 1}`,
    description: `Repository ${index + 1}`,
    htmlUrl: `https://github.com/ls1intum/repo-${index + 1}`,
    updatedAt: new Date('2026-01-01T00:00:00Z').toISOString(),
    stargazersCount: index,
    pullRequestCount: index,
    branchCount: index,
    environmentCount: index,
    latestReleaseTagName: index % 2 === 0 ? `v${index + 1}.0.0` : undefined,
    contributors: [],
  }));

  const getDataView = () => fixture?.debugElement.query(By.directive(DataView)).componentInstance as DataView;

  const createComponent = async (withRepositories = true) => {
    queryClient.clear();
    if (withRepositories) {
      queryClient.setQueryData(getAllRepositoriesQueryKey(), repositories);
    }

    fixture = TestBed.createComponent(RepositoryOverviewComponent);
    component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RepositoryOverviewComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();
    queryClient = TestBed.inject(QueryClient);
  });

  afterEach(() => {
    fixture?.destroy();
    fixture = undefined;
    queryClient.clear();
  });

  it('should create', () => {
    queryClient.setQueryData(getAllRepositoriesQueryKey(), repositories);
    fixture = TestBed.createComponent(RepositoryOverviewComponent);
    component = fixture.componentInstance;
    expect(component).toBeTruthy();
  });

  it('uses the fixed default and binds paginator options', async () => {
    await createComponent();

    expect(component.rows()).toBe(12);
    expect(getDataView().rows).toBe(12);
    expect(getDataView().rowsPerPageOptions).toEqual([6, 12, 24, 48]);
  });

  it('renders loading placeholders with the active row count', async () => {
    await createComponent(false);

    expect(component.rows()).toBe(12);
    expect(getDataView().value).toHaveLength(12);
  });

  it('updates paginator state from PrimeNG page events', async () => {
    await createComponent();

    component.onRepositoryPageChange({ first: 24, rows: 24 });
    fixture?.detectChanges();

    expect(component.rows()).toBe(24);
    expect(component.first()).toBe(24);
  });
});
