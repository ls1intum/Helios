# Client

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 19.0.0-rc.1.


## Requirements
Since this app is implemented using a release candidate version of Angular CLI please make sure that you have following versions
* Node: 22.11.0
* Angular CLI: 19.0.0-rc.1


### Install node

If you have not installed nodejs or the node package manager, install it with nvm:

#### Using Node Version Manager (nvm)

*nvm* ([Node Version Manager](https://github.com/nvm-sh/nvm)) is a tool that allows you to manage multiple versions of Node.js on the same machine. This is particularly useful for testing your application with different Node.js versions or ensuring compatibility across projects.

##### Installing nvm

- **Linux and macOS:**

  * Get the latest version of nvm from the [nvm GitHub repository's Releases page](https://github.com/nvm-sh/nvm/releases).
  * Open a terminal and run the following commands (Do not forget to replace `<LATEST_VERSION_NUMBER>` with an actual version number):

    ```bash
    # You can find the latest version of nvm at GitHub
    curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/<LATEST_VERSION_NUMBER>/install.sh | bash
    ```
  
  * After the installation script completes, add the following lines to your `.bashrc`, `.zshrc`, or `.profile` file (depending on the shell/environment you are using):

    ```bash
    export NVM_DIR="$([ -z "${XDG_CONFIG_HOME-}" ] && printf %s "${HOME}/.nvm" || printf %s "${XDG_CONFIG_HOME}/nvm")"
    [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh" # This loads nvm
    ```
  * Then source the file to add the changes to your current session:
    ```bash
    source ~/.bashrc  # or ~/.zshrc or ~/.profile
    ```
- **Windows:**
  
  Download and install nvm for Windows from the following GitHub repository: [nvm-windows](https://github.com/coreybutler/nvm-windows)
##### Install node using nvm
```bash
nvm install v22.11.0
nvm use v22.11.0
nvm alias default v22.11.0 # Sets the default node version to v22.11.0 system-wide
```
##### Installing Angular CLI
 ```bash
npm install -g @angular/cli@19.0.0-rc.1
```




## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```



## Auto formatting on save using ESLint and VSCode:

Add the following config to your user settings (settings.json):

```json
"eslint.format.enable": true,
"editor.formatOnSave": true,
"editor.codeActionsOnSave": {
  "source.fixAll": "always"
},
```

## Start Client
See the root README.md for information about running the client in docker.

## Running unit tests

To execute unit tests with the [Karma](https://karma-runner.github.io) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
