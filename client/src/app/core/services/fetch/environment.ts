// import { HttpClient } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';
import { CreateQueryResult, injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { MessageService } from 'primeng/api';
// import { lastValueFrom } from 'rxjs';

interface ConnectedSystem {
  id: string;
  name: string;
}

export interface Environment {
  id?: string;
  name: string;
  url: string;
  locked: boolean;
  production: boolean;
  commitHash: string;
  connectedSystems: ConnectedSystem[];
}

let envs: Environment[] = [
  {
    id: '1',
    name: 'artemis-test0',
    url: 'https://artemis-test0.artemis.in.tum.de/',
    locked: true,
    production: false,
    commitHash: 'def789ghi012', // Refers to the second commit in mockCommits
    connectedSystems: [
      { id: 'sys8', name: 'Integrated Code Lifecycle' },
      { id: 'sys9', name: 'MySQL' },
      { id: 'sys10', name: 'Iris' },
      { id: 'sys11', name: 'LTI' },
      { id: 'sys12', name: 'GitHub' },
    ],
  },
  {
    id: '2',
    name: 'artemis-test1',
    url: 'https://artemis-test1.artemis.cit.tum.de/',
    locked: true,
    production: false,
    commitHash: 'ghi345jkl678', // Refers to the third commit in mockCommits
    connectedSystems: [
      { id: 'sys13', name: 'Integrated Code Lifecycle' },
      { id: 'sys14', name: 'MySQL' },
      { id: 'sys15', name: 'LTI' },
      { id: 'sys16', name: 'GitHub' },
    ],
  },
  {
    id: '3',
    name: 'pr*.artemis-k8s',
    url: 'https://pr*.artemis-k8s.ase.cit.tum.de/',
    locked: false,
    production: false,
    commitHash: 'jkl901mno234', // Refers to the fourth commit in mockCommits
    connectedSystems: [
      { id: 'sys17', name: 'Integrated Code Lifecycle' },
      { id: 'sys18', name: 'MySQL' },
      { id: 'sys19', name: 'GitHub' },
    ],
  },
  {
    id: '4',
    name: 'Legacy Testservers',
    url: 'https://artemis-test2.artemis.in.tum.de/',
    locked: true,
    production: false,
    commitHash: 'mno567pqr890', // Refers to the fifth commit in mockCommits
    connectedSystems: [
      { id: 'sys20', name: 'GitLab Test' },
      { id: 'sys21', name: 'Jenkins Test' },
      { id: 'sys22', name: 'Bamboo' },
    ],
  },
];

@Injectable()
export class FetchEnvironmentService {
  // private http = inject(HttpClient);
  private queryClient = inject(QueryClient);
  private messageService = inject(MessageService);

  getEnvironments(): CreateQueryResult<Environment[], string[]> {
    return injectQuery(() => ({
      queryKey: ['environment'],
      // TODO: Replace this mock with the server call below
      queryFn: () => envs,
      // queryFn: () => lastValueFrom(this.http.get<Todo[]>('https://jsonplaceholder.typicode.com/todos'))
    }));
  }

  getEnvironmentById(id: Signal<string>): CreateQueryResult<Environment, string[]> {
    return injectQuery(() => ({
      queryKey: ['environment', id()],
      queryFn: async () => envs.find(env => env.id === id()), //.find(env => env.id === id), // || ['No env found with id ' + id],
      // TODO: Replace this mock with the server call below
      // queryFn: () => lastValueFrom(this.http.get<Todo[]>('https://jsonplaceholder.typicode.com/todos'))
    }));
  }

  upsertEnvironment() {
    return injectMutation(() => ({
      // TODO: Replace this as well as above
      mutationFn: async (environment: Environment) => {
        console.log(environment);
        const i = envs.findIndex(e => e.id === environment.id);
        console.log(i);
        if (i > -1) envs[i] = environment;
        else envs.push(environment);
        console.log(envs);
        return new Promise<Environment>(resolve => resolve(environment));
      },
      onSuccess: () => {
        this.queryClient.invalidateQueries({ queryKey: ['environment'] });
        console.log(123);
        this.messageService.add({ summary: 'Success', severity: 'success' });
      },
    }));
  }
}
