import { Injectable, signal } from "@angular/core";
import { PullRequestInfoDTO } from "../modules/openapi";

@Injectable({
    providedIn: 'root'
})
export class PullRequestStoreService {
    private pullRequestsState = signal<PullRequestInfoDTO[]>([]);

    get pullRequests() {
        return this.pullRequestsState.asReadonly();
    }

    setPullRequests(pullRequests: PullRequestInfoDTO[]) {
        this.pullRequestsState.set(pullRequests);
    }
}
