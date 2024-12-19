/** @type {import('@hey-api/openapi-ts').UserConfig} */
import { defaultPlugins } from '@hey-api/openapi-ts';
module.exports = {
  client: '@hey-api/client-fetch',
  experimentalParser: true,
  input: '../server/application-server/openapi.yaml',
  output: {
    format: 'prettier',
    lint: 'eslint',
    path: 'src/app/core/modules/openapi',
  },
  plugins: [
    ...defaultPlugins,
    '@hey-api/schemas',
    {
      enums: 'javascript',
      name: '@hey-api/typescript',
    },
    '@tanstack/angular-query-experimental',
  ],
};
