// / <reference types="vitest" />
import { defineConfig } from 'vite';
import { coverageConfigDefaults } from 'vitest/config';
import tailwindcss from '@tailwindcss/vite'

import angular from '@analogjs/vite-plugin-angular';
import tsconfigPaths from "vite-tsconfig-paths";

export default defineConfig(({ mode }) => ({
  plugins: [angular(), tsconfigPaths(), tailwindcss()],
  test: {
    pool: 'forks',
    globals: true,
    setupFiles: ['src/test-setup.ts'],
    environment: 'jsdom',
    include: ['src/**/*.{test,spec}.{js,mjs,cjs,ts,mts,cts,jsx,tsx}'],
    coverage: {
      provider: 'istanbul',
      reporter: ['text', 'lcov'],
      exclude: ['**/*.config.*', '**/*.gen.*', '**/assets/**', ...coverageConfigDefaults.exclude],
    }
  },
  define: {
    'import.meta.vitest': mode !== 'production',
  },
}));
