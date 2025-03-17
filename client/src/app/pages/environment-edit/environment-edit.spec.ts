import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EnvironmentEditComponent } from './environment-edit.component';
import { importProvidersFrom } from '@angular/core';
import { TestModule } from '@app/test.module';

describe('BranchDetailsComponent', () => {
  let component: EnvironmentEditComponent;
  let fixture: ComponentFixture<EnvironmentEditComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EnvironmentEditComponent],
      // Todo: figure out how to remove query client provider
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(EnvironmentEditComponent);
    component = fixture.componentInstance;

    // Set input properties
    fixture.componentRef.setInput('repositoryId', 1);
    fixture.componentRef.setInput('id', 1);

    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
