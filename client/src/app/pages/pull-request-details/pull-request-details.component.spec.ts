import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PullRequestDetailsComponent } from './pull-request-details.component';

describe('PullRequestDetailsComponent', () => {
  let component: PullRequestDetailsComponent;
  let fixture: ComponentFixture<PullRequestDetailsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PullRequestDetailsComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(PullRequestDetailsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
