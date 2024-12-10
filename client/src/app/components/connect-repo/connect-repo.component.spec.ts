import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConnectRepoComponent } from './connect-repo.component';

describe('ConnectRepoComponent', () => {
  let component: ConnectRepoComponent;
  let fixture: ComponentFixture<ConnectRepoComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConnectRepoComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ConnectRepoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
