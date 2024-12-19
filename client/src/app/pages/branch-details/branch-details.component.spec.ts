import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BranchDetailsComponent } from './branch-details.component';

describe('BranchDetailsComponent', () => {
  let component: BranchDetailsComponent;
  let fixture: ComponentFixture<BranchDetailsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BranchDetailsComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(BranchDetailsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
