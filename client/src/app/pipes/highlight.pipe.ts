import { Pipe, PipeTransform } from '@angular/core';
@Pipe({
  name: 'highlight',
})
export class HighlightPipe implements PipeTransform {
  transform(text: string | null | undefined, term: string | null | undefined): { text: string; highlight: boolean }[] {
    // Handle edge cases
    if (!text || !term || term.trim() === '') {
      return [{ text: text || '', highlight: false }];
    }

    // Case-insensitive matching
    const regex = new RegExp(`(${term})`, 'gi');
    const parts = text.split(regex);

    // Filter out empty strings and return each part with highlight flag
    return parts
      .filter(part => part !== '')
      .map(part => ({
        text: part,
        highlight: regex.test(part),
      }));
  }
}
