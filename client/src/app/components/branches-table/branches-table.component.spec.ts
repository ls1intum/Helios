import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BranchTableComponent } from './branches-table.component';

describe('BranchTableComponent', () => {
  let component: BranchTableComponent;
  let fixture: ComponentFixture<BranchTableComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BranchTableComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(BranchTableComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
