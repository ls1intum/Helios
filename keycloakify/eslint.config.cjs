const tsPlugin = require('@typescript-eslint/eslint-plugin');
const typescriptParser = require('@typescript-eslint/parser');
const prettierPlugin = require('eslint-plugin-prettier');
const tssUnusedClasses = require('eslint-plugin-tss-unused-classes');
const reactHooks = require('eslint-plugin-react-hooks');
const reactRefresh = require('eslint-plugin-react-refresh');

module.exports = [
    {
        ignores: [
            '.cache/',
            '.angular/',
            '.git/',
            '.github/',
            'build/',
            'dist/',
            'node/',
            'node_modules/',
            '**/*.gen.{ts,tsx}',
        ],
    },
    {
        files: ['src/**/*.{ts,tsx}'],
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
            'react-refresh': reactRefresh,
            'react-hooks': reactHooks,
            'prettier': prettierPlugin,
            'tss-unused-classes': tssUnusedClasses,
        },
        rules: {
            ...tsPlugin.configs.recommended.rules,
            ...reactRefresh.configs.recommended.rules,
            ...reactHooks.configs.recommended.rules,
            ...prettierPlugin.configs.recommended.rules,
            "react-refresh/only-export-components": "off", // TODO Enable this again
            "@typescript-eslint/no-unsafe-declaration-merging": "off", // TODO Enable this again
            "react-hooks/exhaustive-deps": "off",
            "@typescript-eslint/no-redeclare": "off",
            "no-labels": "off",
            'tss-unused-classes/unused-classes': 'warn',
            '@typescript-eslint/no-empty-object-type': 'off',
        },
    },
    {
        files: ["**/*.stories.*"],
        rules: {
            "import/no-anonymous-default-export": "off"
        }
    },
];
