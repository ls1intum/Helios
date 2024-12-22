import { ComponentFixture, TestBed } from '@angular/core/testing';

import { HeliosIconComponent } from './helios-icon.component';

describe('HeliosIconComponent', () => {
  let component: HeliosIconComponent;
  let fixture: ComponentFixture<HeliosIconComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HeliosIconComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(HeliosIconComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
