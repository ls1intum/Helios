import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReleaseCandidateDetailsComponent } from './release-candidate-details.component';
import { importProvidersFrom } from '@angular/core';
import { TestModule } from '@app/test.module';

describe('Integration Test Release Candidate Details Page', () => {
  let component: ReleaseCandidateDetailsComponent;
  let fixture: ComponentFixture<ReleaseCandidateDetailsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReleaseCandidateDetailsComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(ReleaseCandidateDetailsComponent);
    component = fixture.componentInstance;

    // Set input properties
    fixture.componentRef.setInput('name', 'test');

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
