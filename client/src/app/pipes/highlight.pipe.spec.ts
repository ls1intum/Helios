import { HighlightPipe } from './highlight.pipe';

describe('HighlightPipe', () => {
  let highlightPipe: HighlightPipe;

  beforeEach(() => {
    highlightPipe = new HighlightPipe();
  });

  it('should return unaltered text when term is null or empty', () => {
    const result = highlightPipe.transform('example text', '');
    expect(result).toEqual([{ text: 'example text', highlight: false }]);

    const resultNull = highlightPipe.transform('example text', null);
    expect(resultNull).toEqual([{ text: 'example text', highlight: false }]);
  });

  it('should return unaltered text when text is null or undefined', () => {
    const resultNullText = highlightPipe.transform(null, 'term');
    expect(resultNullText).toEqual([{ text: '', highlight: false }]);

    const resultUndefinedText = highlightPipe.transform(undefined, 'term');
    expect(resultUndefinedText).toEqual([{ text: '', highlight: false }]);
  });

  it('should highlight matching text correctly', () => {
    const result = highlightPipe.transform('example text', 'text');
    expect(result).toEqual([
      { text: 'example ', highlight: false },
      { text: 'text', highlight: true },
    ]);
  });

  it('should handle case-insensitive matches correctly', () => {
    const result = highlightPipe.transform('Example Text', 'text');
    expect(result).toEqual([
      { text: 'Example ', highlight: false },
      { text: 'Text', highlight: true },
    ]);
  });

  it('should highlight multiple occurrences correctly', () => {
    const result = highlightPipe.transform('text example text', 'text');
    expect(result).toEqual([
      { text: 'text', highlight: true },
      { text: ' example ', highlight: false },
      { text: 'text', highlight: true },
    ]);
  });

  it('should return unaltered text when term does not match', () => {
    const result = highlightPipe.transform('example text', 'term');
    expect(result).toEqual([{ text: 'example text', highlight: false }]);
  });

  it('should handle empty text input correctly', () => {
    const result = highlightPipe.transform('', 'term');
    expect(result).toEqual([{ text: '', highlight: false }]);
  });

  it('should handle string with spaces at the beginning and end correctly', () => {
    const result = highlightPipe.transform(' text ', 'text');
    expect(result).toEqual([
      { text: ' ', highlight: false },
      { text: 'text', highlight: true },
      { text: ' ', highlight: false },
    ]);
  });
});
