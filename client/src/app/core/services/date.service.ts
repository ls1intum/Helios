import { DatePipe } from "@angular/common";
import { inject } from "@angular/core";

export class DateService {
  private datePipe = inject(DatePipe);

  formatDate(date: string): string | null {
    return date ? this.datePipe.transform(date, 'd MMMM y, h:mm a') : null;
  }
}
