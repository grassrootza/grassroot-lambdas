import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { PagerankProfileComponent } from './pagerank-profile.component';

describe('PagerankProfileComponent', () => {
  let component: PagerankProfileComponent;
  let fixture: ComponentFixture<PagerankProfileComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ PagerankProfileComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(PagerankProfileComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
