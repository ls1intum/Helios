import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LockTagComponent } from './lock-tag.component';

describe('LockTagComponent', () => {
  let component: LockTagComponent;
  let fixture: ComponentFixture<LockTagComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LockTagComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(LockTagComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
