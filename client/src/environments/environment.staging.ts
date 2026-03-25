export const environment = {
  production: false,
  sentry: {
    enabled: false,
    dsn: '',
  },
  serverUrl: 'https://helios-staging.aet.cit.tum.de',
  keycloak: {
    url: 'https://helios-staging.aet.cit.tum.de',
    realm: 'helios',
    clientId: 'helios-app',
    skipLoginPage: true,
  },
  clientUrl: 'https://helios-staging.aet.cit.tum.de',
  heliosDevelopers: ['meryemefe', 'mertilginoglu', 'gbanu', 'thielpa', 'egekocabas', 'turkerkoc', 'stefannemeth', 'bensofficial'],
};
