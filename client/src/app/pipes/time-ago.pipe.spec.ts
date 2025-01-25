import { TimeAgoPipe } from './time-ago.pipe';

describe('GreetPipe', () => {
  let timeAgoPipe: TimeAgoPipe;

  beforeEach(() => {
    timeAgoPipe = new TimeAgoPipe();
  });

  it('transforms current time correctly', () => {
    expect(timeAgoPipe.transform(new Date().toString())).toBe('a few seconds ago');
  });
});
