import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LockingThresholdsComponent } from './locking-thresholds.component';

describe('LockingThresholdsComponent', () => {
  let component: LockingThresholdsComponent;
  let fixture: ComponentFixture<LockingThresholdsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LockingThresholdsComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(LockingThresholdsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
