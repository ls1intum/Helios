import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PullRequestTableComponent } from './pull-request-table.component';

describe('PullRequestTableComponent', () => {
  let component: PullRequestTableComponent;
  let fixture: ComponentFixture<PullRequestTableComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PullRequestTableComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PullRequestTableComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
