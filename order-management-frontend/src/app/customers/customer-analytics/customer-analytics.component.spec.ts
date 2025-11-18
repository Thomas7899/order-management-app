import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CustomerAnalyticsComponent } from './customer-analytics.component';

describe('CustomerAnalyticsComponent', () => {
  let component: CustomerAnalyticsComponent;
  let fixture: ComponentFixture<CustomerAnalyticsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CustomerAnalyticsComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CustomerAnalyticsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
