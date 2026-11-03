import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ReleaseEvaluationDialogComponent, ReleaseEvaluationDialogData } from './release-evaluation-dialog.component';

describe('Release evaluation dialog', () => {
  let fixture: ComponentFixture<ReleaseEvaluationDialogComponent>;
  const close = vi.fn();
  async function render(data: ReleaseEvaluationDialogData) {
    await TestBed.configureTestingModule({
      imports: [ReleaseEvaluationDialogComponent],
      providers: [
        { provide: DynamicDialogRef, useValue: { close } },
        { provide: DynamicDialogConfig, useValue: { data } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ReleaseEvaluationDialogComponent);
    fixture.detectChanges();
  }
  function click(label: string) {
    const button = Array.from(fixture.nativeElement.querySelectorAll('button')).find(element => (element as HTMLElement).textContent?.trim() === label) as HTMLButtonElement;
    expect(button).toBeDefined();
    button.click();
  }
  beforeEach(() => close.mockReset());
  it.each([true, false])('shows the release name and working status %s', async isWorking => {
    await render({ releaseName: 'v2.0-rc1', isWorking, comment: 'Previous evaluation' });
    expect(fixture.nativeElement.textContent).toContain('v2.0-rc1');
    expect(fixture.nativeElement.textContent).toContain(isWorking ? 'Working' : 'Broken');
    expect((fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement).value).toBe('Previous evaluation');
    expect(fixture.nativeElement.textContent).toContain('19/500');
  });
  it('accepts an evaluation without a comment', async () => {
    await render({ releaseName: 'v2.0-rc1', isWorking: true });
    click('Submit Evaluation');
    expect(close).toHaveBeenCalledExactlyOnceWith({ isWorking: true, comment: '' });
  });
  it('submits edited comments with the selected status', async () => {
    await render({ releaseName: 'v2.0-rc1', isWorking: false });
    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = 'Login fails after deployment';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    click('Submit Evaluation');
    expect(close).toHaveBeenCalledExactlyOnceWith({ isWorking: false, comment: 'Login fails after deployment' });
    expect(fixture.nativeElement.textContent).toContain('28/500');
  });
  it('accepts the maximum comment length', async () => {
    await render({ releaseName: 'v2.0-rc1', isWorking: true });
    fixture.componentInstance.evaluationForm.controls.comment.setValue('a'.repeat(500));
    click('Submit Evaluation');
    expect(close).toHaveBeenCalledWith({ isWorking: true, comment: 'a'.repeat(500) });
  });
  it('keeps the dialog open if a comment exceeds 500 characters', async () => {
    await render({ releaseName: 'v2.0-rc1', isWorking: true });
    fixture.componentInstance.evaluationForm.controls.comment.setValue('a'.repeat(501));
    click('Submit Evaluation');
    expect(close).not.toHaveBeenCalled();
    expect(fixture.componentInstance.evaluationForm.controls.comment.hasError('maxlength')).toBe(true);
  });
  it('closes without submitting when cancelled', async () => {
    await render({ releaseName: 'v2.0-rc1', isWorking: false, comment: 'Unsaved' });
    click('Cancel');
    expect(close).toHaveBeenCalledExactlyOnceWith();
  });
});
