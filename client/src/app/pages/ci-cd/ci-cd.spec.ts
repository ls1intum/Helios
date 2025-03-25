import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CiCdComponent } from './ci-cd.component';
import { importProvidersFrom } from '@angular/core';
import { TestModule } from '@app/test.module';

class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

describe('Integration Test CI/CD Page', () => {
  let component: CiCdComponent;
  let fixture: ComponentFixture<CiCdComponent>;

  window.ResizeObserver = ResizeObserver;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CiCdComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(CiCdComponent);
    component = fixture.componentInstance;

    // Set input properties
    fixture.componentRef.setInput('repositoryId', 1);

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
