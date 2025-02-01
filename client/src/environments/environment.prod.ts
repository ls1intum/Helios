export const environment = {
  production: true,
  sentry: {
    enabled: true,
    dsn: 'https://8f1ada8034a481454b663bb091cd0eb9@sentry.ase.in.tum.de/4',
  },
  serverUrl: 'https://helios.aet.cit.tum.de',
  keycloak: {
    url: 'https://helios.aet.cit.tum.de',
    realm: 'helios',
    clientId: 'helios-app',
    skipLoginPage: true,
  },
  clientUrl: 'https://helios.aet.cit.tum.de',
};
