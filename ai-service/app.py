"""Private mTLS service. Public clients must use the authenticated Java gateway."""
from contextlib import asynccontextmanager
from concurrent.futures import ThreadPoolExecutor
import asyncio
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field
from config import Settings
from images import decode_image
from face_engine import FaceEngine, ModelUnavailable
from ocr_engine import OcrEngine
from rag_engine import RagEngine
from forecast_engine import ForecastEngine


settings = Settings.load()
ocr, face, rag = OcrEngine(settings), FaceEngine(settings), RagEngine(settings)
forecaster = ForecastEngine(settings)
workers = ThreadPoolExecutor(max_workers=2, thread_name_prefix="ai-inference")
# A timed-out inference keeps its slot until the worker really finishes; no unbounded task queue.
slots = asyncio.Semaphore(2)


@asynccontextmanager
async def lifespan(app):
    yield
    workers.shutdown(wait=False, cancel_futures=True)


app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None, lifespan=lifespan)


class OcrRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    image: str = Field(max_length=6_990_508)


class FaceRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    id_photo: str = Field(max_length=6_990_508)
    live_face: str = Field(max_length=6_990_508)


class RagRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    message: str = Field(min_length=1, max_length=4000)
    audience: str
    language: str = "en"


class ForecastRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    start_date: str
    days: int = Field(ge=1, le=90)


@app.exception_handler(ModelUnavailable)
async def unavailable(request, exception):
    return JSONResponse(status_code=503, content={"error_code": "AI_UNAVAILABLE", "message": "Manual verification required"})


@app.exception_handler(ValueError)
async def invalid(request, exception):
    return JSONResponse(status_code=422, content={"error_code": "INVALID_AI_INPUT", "message": "Manual verification required"})


from fastapi.exceptions import RequestValidationError


@app.exception_handler(RequestValidationError)
async def validation(request, exception):
    # FastAPI's default validation errors may echo the raw image or message.
    return JSONResponse(status_code=422, content={"error_code": "INVALID_AI_INPUT", "message": "Invalid request"})


@app.middleware("http")
async def bounded_body(request: Request, call_next):
    # Bound actual bytes, including chunked requests, before Pydantic parses Base64.
    total = 0
    parts = []
    async for chunk in request.stream():
        total += len(chunk)
        if total > 14_000_000:
            return JSONResponse(status_code=413, content={"error_code": "PAYLOAD_TOO_LARGE"})
        parts.append(chunk)
    request._body = b"".join(parts)
    return await call_next(request)


async def inference(function, *args):
    try:
        await asyncio.wait_for(slots.acquire(), timeout=0.01)
    except TimeoutError as exc:
        raise ModelUnavailable() from exc
    loop = asyncio.get_running_loop()
    future = loop.run_in_executor(workers, function, *args)
    def finished(completed):
        slots.release()
        if not completed.cancelled():
            completed.exception()  # Consume late failures after an HTTP timeout without logging payloads.
    future.add_done_callback(finished)
    try:
        return await asyncio.wait_for(asyncio.shield(future), timeout=2.8)
    except TimeoutError as exc:
        raise ModelUnavailable() from exc


@app.get("/health")
def health():
    # Liveness does not imply that approved weights/datasets have passed acceptance.
    return {"status": "UP", "acceptance": "PENDING"}


@app.post("/ocr")
async def id_ocr(body: OcrRequest):
    return await inference(lambda: ocr.recognize(decode_image(body.image)))


@app.post("/face-match")
async def face_match(body: FaceRequest):
    return await inference(lambda: face.match(decode_image(body.id_photo), decode_image(body.live_face)))


@app.post("/rag/query")
async def rag_query(body: RagRequest):
    return await inference(rag.query, body.message, body.audience, body.language)


@app.post("/forecast")
async def forecast(body: ForecastRequest):
    return await inference(forecaster.forecast, body.start_date, body.days)
