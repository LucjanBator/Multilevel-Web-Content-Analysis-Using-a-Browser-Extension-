import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ParserResults } from './parser-results';

describe('ParserResults', () => {
  let component: ParserResults;
  let fixture: ComponentFixture<ParserResults>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ParserResults]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ParserResults);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
