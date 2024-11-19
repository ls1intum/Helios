import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EnvironmentCommitInfoComponent } from './environment-commit-info.component';

describe('EnvironmentCommitInfoComponent', () => {
  let component: EnvironmentCommitInfoComponent;
  let fixture: ComponentFixture<EnvironmentCommitInfoComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EnvironmentCommitInfoComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EnvironmentCommitInfoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
