import hmac
import hashlib
from contextlib import asynccontextmanager
from fastapi import Body, FastAPI, HTTPException, Header, Request, status
from pydantic import BaseModel
from nats.js.api import DiscardPolicy, StreamConfig
from nats.js.errors import BadRequestError
from app.config import settings
from app.logger import logger
from app.nats_client import nats_client

RETENTION_DAYS = 7
SECONDS_PER_DAY = 24 * 60 * 60
RETENTION_MAX_AGE_SECONDS = RETENTION_DAYS * SECONDS_PER_DAY

GIB = 1024 ** 3
MIB = 1024 ** 2

# Returned by add_stream when a stream with the given name already exists
# with a different configuration. When the existing config matches deeply,
# add_stream is idempotent and returns success.
ERR_STREAM_NAME_EXISTS = 10058


async def ensure_stream(name: str, subjects: list[str], max_bytes: int) -> None:
    # Default Limits retention preserves the time-bounded replay that the
    # application-server's durable consumer relies on after a restart or after
    # NATS auto-deletes an inactive consumer. max_bytes bounds disk usage;
    # DiscardPolicy.NEW propagates backpressure to GitHub (which retries
    # webhook deliveries) rather than silently dropping the oldest events.
    stream_config = StreamConfig(
        name=name,
        subjects=subjects,
        storage="file",
        max_age=RETENTION_MAX_AGE_SECONDS,
        max_bytes=max_bytes,
        discard=DiscardPolicy.NEW,
    )
    try:
        await nats_client.js.add_stream(config=stream_config)
        return
    except BadRequestError as error:
        if getattr(error, "err_code", None) != ERR_STREAM_NAME_EXISTS:
            raise

    await nats_client.js.update_stream(config=stream_config)
    logger.info("Updated existing stream configuration for '%s'", name)


@asynccontextmanager
async def lifespan(app: FastAPI):
    await nats_client.connect()
    await ensure_stream(name="github", subjects=["github.>"], max_bytes=2 * GIB)
    await ensure_stream(name="notification", subjects=["notification.>"], max_bytes=64 * MIB)
    yield
    await nats_client.close()


app = FastAPI(lifespan=lifespan)


def verify_github_signature(signature, secret, body):
    mac = hmac.new(secret.encode(), body, hashlib.sha1)
    expected_signature = "sha1=" + mac.hexdigest()
    return hmac.compare_digest(signature, expected_signature)


@app.post("/github")
async def github_webhook(
    request: Request, 
    signature: str = Header(
        None, 
        alias="X-Hub-Signature", 
        description="GitHub's HMAC hex digest of the payload, used for verifying the webhook's authenticity"
    ), 
    event_type: str = Header(
        None, 
        alias="X-Github-Event",
        description="The type of event that triggered the webhook, such as 'push', 'pull_request', etc.",
    ),
    body = Body(...),
):    
    body = await request.body()
    
    if not verify_github_signature(signature, settings.WEBHOOK_SECRET, body):
        raise HTTPException(status_code=401, detail="Invalid signature")
    
    # Ignore ping events
    if event_type == "ping":
        return { "status": "pong" }
    
    # Extract subject from the payload
    payload = await request.json()

    org = "?"
    repo = "?"
    if "repository" in payload:
        org = payload["repository"]["owner"]["login"]
        repo = payload["repository"]["name"]
    elif "organization" in payload:
        org = payload["organization"]["login"]
    
    org_sanitized = org.replace('.', '~')
    repo_sanitized = repo.replace('.', '~')

    subject = f"github.{org_sanitized}.{repo_sanitized}.{event_type}"

    # Publish the payload to NATS JetStream
    await nats_client.publish_with_retry(subject, body)

    return { "status": "ok" }


class HealthCheck(BaseModel):
    """Response model to validate and return when performing a health check."""

    status: str = "OK"


@app.get(
    "/health",
    tags=["healthcheck"],
    summary="Perform a Health Check",
    response_description="Return HTTP Status Code 200 (OK)",
    status_code=status.HTTP_200_OK,
    response_model=HealthCheck,
)
def get_health() -> HealthCheck:
    """
    ## Perform a Health Check
    Endpoint to perform a healthcheck on. This endpoint can primarily be used Docker
    to ensure a robust container orchestration and management is in place. Other
    services which rely on proper functioning of the API service will not deploy if this
    endpoint returns any other HTTP status code except 200 (OK).
    Returns:
        HealthCheck: Returns a JSON response with the health status
    """
    return HealthCheck(status="OK")
