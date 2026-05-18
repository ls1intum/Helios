import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  let store: Record<string, string>;
  let originalLocalStorage: Storage;
  let originalMatchMedia: typeof window.matchMedia;

  beforeEach(() => {
    store = {};
    originalLocalStorage = window.localStorage;
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      value: {
        getItem: (k: string) => store[k] ?? null,
        setItem: (k: string, v: string) => {
          store[k] = v;
        },
        removeItem: (k: string) => {
          delete store[k];
        },
        clear: () => {
          store = {};
        },
        key: () => null,
        length: 0,
      } as Storage,
    });
    originalMatchMedia = window.matchMedia;
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: () => ({ matches: false } as MediaQueryList),
    });
  });

  afterEach(() => {
    Object.defineProperty(window, 'localStorage', { configurable: true, value: originalLocalStorage });
    Object.defineProperty(window, 'matchMedia', { configurable: true, value: originalMatchMedia });
  });

  function getService(): ThemeService {
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
    return TestBed.inject(ThemeService);
  }

  it('initializes from localStorage when "dark" is saved', () => {
    store.theme = 'dark';
    const service = getService();
    expect(service.isDarkMode()).toBe(true);
  });

  it('initializes from localStorage when "light" is saved', () => {
    store.theme = 'light';
    const service = getService();
    expect(service.isDarkMode()).toBe(false);
  });

  it('toggle flips the signal and writes to localStorage', () => {
    const service = getService();
    const initial = service.isDarkMode();
    service.toggle();
    expect(service.isDarkMode()).toBe(!initial);
    expect(store.theme).toBe(!initial ? 'dark' : 'light');
  });
});
