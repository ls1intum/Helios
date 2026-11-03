const tsPlugin = require('@typescript-eslint/eslint-plugin');
const angular = require('angular-eslint');
const angularPlugin = angular.tsPlugin;
const angularTemplateParser = require('@angular-eslint/template-parser');
const typescriptParser = require('@typescript-eslint/parser');
const prettierPlugin = require('eslint-plugin-prettier');

module.exports = [
  {
    ignores: [
      '.cache/',
      '.angular/',
      '.git/',
      '.github/',
      'build/',
      'coverage/',
      'node/',
      'node_modules/',
      'src/app/core/modules/openapi/',
    ],
  },
  {
    files: ['src/**/*.ts'],
    languageOptions: {
      parser: typescriptParser,
      parserOptions: {
        project: [
          './tsconfig.json',
          './tsconfig.app.json',
          './tsconfig.spec.json',
        ],
      },
    },
    plugins: {
      '@typescript-eslint': tsPlugin,
      '@angular-eslint': angularPlugin,
      'prettier': prettierPlugin,
    },
    rules: {
      ...prettierPlugin.configs.recommended.rules,
      ...tsPlugin.configs.recommended.rules,
      ...Object.assign({}, ...angular.configs.tsRecommended.map(config => config.rules)),
      '@angular-eslint/directive-selector': [
        'warn',
        {
          type: 'attribute',
          prefix: ['app'],
          style: 'camelCase',
        },
      ],
      '@angular-eslint/component-selector': [
        'warn',
        {
          type: 'element',
          prefix: ['app'],
          style: 'kebab-case',
        },
      ],
      '@typescript-eslint/no-empty-object-type': 'off',
      // Angular 22 migrations retain eager change detection to preserve behavior.
      '@angular-eslint/prefer-on-push-component-change-detection': 'off',
    },
  },
  {
    files: ['src/**/*.html'],
    languageOptions: {
      parser: angularTemplateParser,
    },
    plugins: {
      '@angular-eslint': angularPlugin,
      'prettier': prettierPlugin,
    },
    rules: {
      'prettier/prettier': ['error', { parser: 'angular' }],
    },
  },
];
