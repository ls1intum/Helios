/** @type {import('@hey-api/openapi-ts').UserConfig} */
import { defaultPlugins } from '@hey-api/openapi-ts';

const pluginsWithoutTypeScript = defaultPlugins.filter((plugin) => {
  if (plugin === '@hey-api/typescript') {
    return false;
  }
  return !(typeof plugin === 'object' && plugin?.name === '@hey-api/typescript');
});

module.exports = {
  experimentalParser: true,
  input: '../server/application-server/openapi.yaml',
  output: {
    postProcess: ['prettier'],
    path: 'src/app/core/modules/openapi',
  },
  plugins: [
    ...pluginsWithoutTypeScript,
    '@hey-api/schemas',
    {
      enums: 'javascript',
      name: '@hey-api/typescript',
    },
    '@tanstack/angular-query-experimental',
    '@hey-api/client-fetch',
  ],
};
