import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ImprintComponent } from './imprint.component';
import { importProvidersFrom } from '@angular/core';
import { TestModule } from '@app/test.module';

describe('Integration Test Imprint Page', () => {
  let component: ImprintComponent;
  let fixture: ComponentFixture<ImprintComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ImprintComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(ImprintComponent);
    component = fixture.componentInstance;

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
