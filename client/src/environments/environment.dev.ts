export const environment = {
  production: false,
  serverUrl: 'http://localhost:8080',
  keycloak: {
    url: 'http://localhost:8081',
    realm: 'helios',
    clientId: 'helios-app',
    skipLoginPage: true
  },
  clientUrl: 'http://localhost:4200'
};
