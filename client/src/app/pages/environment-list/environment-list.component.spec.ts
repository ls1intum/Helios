import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EnvironmentListComponent } from './environment-list.component';

describe('EnvironmentListComponent', () => {
  let component: EnvironmentListComponent;
  let fixture: ComponentFixture<EnvironmentListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EnvironmentListComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EnvironmentListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
