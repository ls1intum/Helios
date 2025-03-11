import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReleaseCanidateListComponent } from './release-candidate-list.component';
import { importProvidersFrom } from '@angular/core';
import { TestModule } from '@app/test.module';

describe('Integration Test Release Candidate List Page', () => {
  let component: ReleaseCanidateListComponent;
  let fixture: ComponentFixture<ReleaseCanidateListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReleaseCanidateListComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(ReleaseCanidateListComponent);
    component = fixture.componentInstance;

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
