import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EnvironmentEditFormComponent } from './environment-edit-form.component';

describe('EnvironmentEditFormComponent', () => {
  let component: EnvironmentEditFormComponent;
  let fixture: ComponentFixture<EnvironmentEditFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EnvironmentEditFormComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EnvironmentEditFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
