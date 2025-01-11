import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileNavSectionComponent } from './profile-nav-section.component';

describe('ProfileNavSectionComponent', () => {
  let component: ProfileNavSectionComponent;
  let fixture: ComponentFixture<ProfileNavSectionComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfileNavSectionComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileNavSectionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
