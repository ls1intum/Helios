export function extractWorkflowRunId(workflowRunUrl?: string): number | undefined {
  const matches = workflowRunUrl?.match(/\/runs\/(\d+)(?:\/)?(?:[?#].*)?$/);
  if (!matches?.[1]) {
    return undefined;
  }

  return parseInt(matches[1], 10);
}
