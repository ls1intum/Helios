import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReleaseComponent } from './release.component';
import { importProvidersFrom } from '@angular/core';
import { TestModule } from '@app/test.module';

describe('Integration Test Release Page', () => {
  let component: ReleaseComponent;
  let fixture: ComponentFixture<ReleaseComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReleaseComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(ReleaseComponent);
    component = fixture.componentInstance;

    // Set input properties
    fixture.componentRef.setInput('repositoryId', 1);

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
