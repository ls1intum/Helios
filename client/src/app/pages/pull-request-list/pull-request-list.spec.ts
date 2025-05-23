import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PullRequestListComponent } from './pull-request-list.component';
import { importProvidersFrom } from '@angular/core';
import { TestModule } from '@app/test.module';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { PAGINATED_FILTER_OPTIONS_TOKEN, PaginatedFilterOption } from '@app/core/services/paginated-table.service';

describe('Integration Test Pull Request List Page', () => {
  let component: PullRequestListComponent;
  let fixture: ComponentFixture<PullRequestListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PullRequestListComponent],
      providers: [
        importProvidersFrom(TestModule),
        // Mock KeycloakService
        {
          provide: KeycloakService,
          useValue: {
            isLoggedIn: () => false,
          },
        },
        // Provide filtered options through a factory
        {
          provide: PAGINATED_FILTER_OPTIONS_TOKEN,
          useFactory: (): PaginatedFilterOption[] => {
            return [{ name: 'Open pull requests', value: 'OPEN' }];
          },
          deps: [KeycloakService],
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PullRequestListComponent);
    component = fixture.componentInstance;

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
