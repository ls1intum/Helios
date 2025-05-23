import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BranchListComponent } from './branch-list.component';
import { importProvidersFrom } from '@angular/core';
import { TestModule } from '@app/test.module';
import { FILTER_OPTIONS_TOKEN, FilterOption } from '@app/core/services/search-table.service';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { BranchInfoWithLink } from '@app/components/branches-table/branches-table.component';

describe('Integration Test Branch List Page', () => {
  let component: BranchListComponent;
  let fixture: ComponentFixture<BranchListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BranchListComponent],
      providers: [
        importProvidersFrom(TestModule),
        // Mock KeycloakService
        {
          provide: KeycloakService,
          useValue: {
            isLoggedIn: () => false,
            getPreferredUsername: () => '',
          },
        },
        // Provide FILTER_OPTIONS_TOKEN manually to match factory expectations
        {
          provide: FILTER_OPTIONS_TOKEN,
          useFactory: (): FilterOption<BranchInfoWithLink>[] => [
            {
              name: 'All Branches',
              filter: (branches: BranchInfoWithLink[]) => branches,
            },
          ],
          deps: [KeycloakService],
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BranchListComponent);
    component = fixture.componentInstance;

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
