import { DatePipe } from '@angular/common';
import { inject, Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class DateService {
  private datePipe = inject(DatePipe);

  formatDate = (date?: string, formatString: string = 'd. MMMM y'): string | null => {
    return date ? this.datePipe.transform(date, formatString) : null;
  };
}
