import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from 'environments/environment';
import { KeycloakService } from './keycloak/keycloak.service';
import { RepositoryService } from './repository.service';

export type WorkflowDeploymentJobDetectionStatus = 'FOUND' | 'NOT_FOUND' | 'UNCLEAR' | 'ERROR';

export interface WorkflowDeploymentJobDetectionResponse {
  workflowId: number;
  workflowPath: string;
  ref: string;
  deploymentJobName: string | null;
  status: WorkflowDeploymentJobDetectionStatus;
  message: string;
}

@Injectable({
  providedIn: 'root',
})
export class WorkflowAiService {
  private readonly http = inject(HttpClient);
  private readonly keycloakService = inject(KeycloakService);
  private readonly repositoryService = inject(RepositoryService);
  private readonly baseUrl = environment.serverUrl.replace(/\/$/, '');

  detectDeploymentJob(repositoryId: number, workflowId: number): Observable<WorkflowDeploymentJobDetectionResponse> {
    const token = this.keycloakService.keycloak.token;
    const currentRepositoryId = this.repositoryService.currentRepositoryId();
    return this.http.post<WorkflowDeploymentJobDetectionResponse>(
      `${this.baseUrl}/api/settings/${repositoryId}/workflows/${workflowId}/detect-deployment-job`,
      {},
      {
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...(currentRepositoryId ? { 'X-Repository-Id': String(currentRepositoryId) } : {}),
        },
      }
    );
  }
}
