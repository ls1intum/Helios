// import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { CreateQueryResult, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { MessageService } from 'primeng/api';
// import { lastValueFrom } from 'rxjs';

interface Commit {
  commitHash: string;
  author: {
    name: string;
    email: string;
  };
  committer: {
    name: string;
    email: string;
    date: string; // ISO 8601 format
  };
  message: string;
  prStatus: 'OPEN' | 'MERGED' | 'CLOSED'; // Pull request status
  tags: string[];
  branch: string;
  repository: {
    name: string;
    url: string;
  };
}

let commits: Commit[] = [
  {
    commitHash: 'abc123def456',
    author: {
      name: 'John Doe',
      email: 'john.doe@example.com',
    },
    committer: {
      name: 'Jane Smith',
      email: 'jane.smith@example.com',
      date: '2024-11-15T10:30:00Z',
    },
    message: 'Fix bug in authentication service',
    prStatus: 'MERGED',
    tags: ['bugfix', 'auth'],
    branch: 'main',
    repository: {
      name: 'auth-service',
      url: 'https://github.com/example-org/auth-service',
    },
  },
  {
    commitHash: 'def789ghi012',
    author: {
      name: 'Alice Johnson',
      email: 'alice.johnson@example.com',
    },
    committer: {
      name: 'Bob Brown',
      email: 'bob.brown@example.com',
      date: '2024-11-14T14:45:00Z',
    },
    message: 'Add new feature for data export',
    prStatus: 'OPEN',
    tags: ['feature', 'export'],
    branch: 'feature/data-export',
    repository: {
      name: 'data-processor',
      url: 'https://github.com/example-org/data-processor',
    },
  },
  {
    commitHash: 'ghi345jkl678',
    author: {
      name: 'Emily White',
      email: 'emily.white@example.com',
    },
    committer: {
      name: 'Chris Green',
      email: 'chris.green@example.com',
      date: '2024-11-13T09:20:00Z',
    },
    message: 'Update documentation for API endpoints',
    prStatus: 'CLOSED',
    tags: ['docs', 'api'],
    branch: 'docs/update-api',
    repository: {
      name: 'api-docs',
      url: 'https://github.com/example-org/api-docs',
    },
  },
  {
    commitHash: 'jkl901mno234',
    author: {
      name: 'David Black',
      email: 'david.black@example.com',
    },
    committer: {
      name: 'Susan Blue',
      email: 'susan.blue@example.com',
      date: '2024-11-12T17:15:00Z',
    },
    message: 'Refactor database queries for performance',
    prStatus: 'MERGED',
    tags: ['refactor', 'performance'],
    branch: 'main',
    repository: {
      name: 'database-service',
      url: 'https://github.com/example-org/database-service',
    },
  },
  {
    commitHash: 'mno567pqr890',
    author: {
      name: 'Tom Gray',
      email: 'tom.gray@example.com',
    },
    committer: {
      name: 'Laura Pink',
      email: 'laura.pink@example.com',
      date: '2024-11-10T11:00:00Z',
    },
    message: 'Initial commit for new project',
    prStatus: 'MERGED',
    tags: ['init', 'new-project'],
    branch: 'main',
    repository: {
      name: 'new-project',
      url: 'https://github.com/example-org/new-project',
    },
  },
];

@Injectable()
export class FetchCommitService {
  // private http = inject(HttpClient);
  private queryClient = inject(QueryClient);
  private messageService = inject(MessageService);

  getCommits(): CreateQueryResult<Commit[], string[]> {
    return injectQuery(() => ({
      queryKey: ['commits'],
      // TODO: Replace this mock with the server call below
      queryFn: () => commits,
      // queryFn: () => lastValueFrom(this.http.get<Todo[]>('https://jsonplaceholder.typicode.com/todos'))
    }));
  }
}
