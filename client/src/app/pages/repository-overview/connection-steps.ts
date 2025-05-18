export const connectionSteps = [
  {
    title: 'Define Your GitHub Environments',
    description: 'First, set up the environments you want to deploy to in your GitHub repository settings.',
    icon: 'brand-github',
    image: 'assets/github-environments.png',
    instructions: [
      'Navigate to your GitHub repository settings',
      'Open the "Environments" section',
      'Create environments (e.g., staging, production, test-server1, test-server2)',
      'Configure environment-specific protection rules if needed',
    ],
  },
  {
    title: 'Configure Deployment Workflow',
    description: 'Create a GitHub Actions workflow file that will handle your deployments through Helios.',
    icon: 'code',
    instructions: [
      'Create a workflow file (e.g., deploy-with-helios.yml)',
      'Ensure it has workflow_dispatch trigger',
      'Include required inputs: branch_name, commit_sha, environment_name, triggered_by',
      'Set up environment at the job level',
      'Use concurrency to ensure only one deployment runs per environment',
    ],
    codeExample: `name: Deploy with Helios
on:
  workflow_dispatch:
    inputs:
      branch_name:
        description: "Which branch to deploy"
        required: true
        type: string
      commit_sha:
        description: 'Commit SHA to deploy'
        required: false
      environment_name:
        description: "Which environment to deploy"
        required: true
        type: string
      triggered_by:
        description: "Username that triggered deployment"
        required: false
        type: string
concurrency:
  group: \${{ github.event.inputs.environment_name }}
jobs:
  deploy:
    runs-on: ubuntu-latest
    environment: \${{ github.event.inputs.environment_name }}
    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          ref: \${{ github.event.inputs.branch_name }}
      # Add your deployment steps here`,
  },
  {
    title: 'Install the Helios GitHub App',
    description: 'Install the Helios GitHub App to connect your repository to the Helios platform.',
    icon: 'sun',
    instructions: [
      'Click the "Install Helios App" button below',
      'Follow the installation process',
      'Select which repositories to connect',
      "Verify the app appears in your repository's installed apps",
      "If you don't have admin permissions, ask an organization admin to install it",
    ],
    actionButton: {
      label: 'Install Helios App',
      icon: 'brand-github',
      url: 'https://github.com/apps/helios-aet',
    },
  },
  {
    title: 'Configure Project Settings in Helios',
    description: 'Set up your repository in the Helios project settings.',
    icon: 'settings',
    image: 'assets/project-settings.png',
    instructions: [
      'Find your repository and navigate to project settings',
      'Adjust lock reservation/expiration times if needed',
      'Create workflow groups for logical organization',
      'Configure your test workflow with label TEST and select artifact name for test analysis',
    ],
  },
  {
    title: 'Configure Environments in Helios',
    description: 'Configure and enable environment you want to be able to deploy to.',
    icon: 'server',
    image: 'assets/edit-environments.png',
    instructions: [
      'In your repository navigate to environments',
      'Edit the environment',
      'Select deployment workflow to enable environment',
      'Optionally fill other fields to enable other features like status checks',
    ],
  },
];
