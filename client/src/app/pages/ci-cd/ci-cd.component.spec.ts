import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CiCdComponent } from './ci-cd.component';

describe('CiCdComponent', () => {
  let component: CiCdComponent;
  let fixture: ComponentFixture<CiCdComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CiCdComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(CiCdComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
