import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ReleaseComponent } from './release.component';

describe('ReleaseComponent', () => {
  let component: ReleaseComponent;
  let fixture: ComponentFixture<ReleaseComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReleaseComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ReleaseComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
