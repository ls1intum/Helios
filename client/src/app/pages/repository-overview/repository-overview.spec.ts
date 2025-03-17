import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RepositoryOverviewComponent } from './repository-overview.component';
import { importProvidersFrom } from '@angular/core';
import { TestModule } from '@app/test.module';

describe('Integration Test Repository Overview Page', () => {
  let component: RepositoryOverviewComponent;
  let fixture: ComponentFixture<RepositoryOverviewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RepositoryOverviewComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(RepositoryOverviewComponent);
    component = fixture.componentInstance;

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
