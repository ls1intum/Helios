export const environment = {
  production: false,
  sentry: {
    enabled: false,
    dsn: '',
  },
  serverUrl: 'http://localhost:8080',
  keycloak: {
    url: 'http://localhost:8081',
    realm: 'helios-example',
    clientId: 'helios-app-example',
    skipLoginPage: true,
  },
  clientUrl: 'http://localhost:4200',
};
