import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PullRequestListComponent } from './pull-request-list.component';
import { importProvidersFrom } from '@angular/core';
import { TestModule } from '@app/test.module';

describe('Integration Test Pull Request List Page', () => {
  let component: PullRequestListComponent;
  let fixture: ComponentFixture<PullRequestListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PullRequestListComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(PullRequestListComponent);
    component = fixture.componentInstance;

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
