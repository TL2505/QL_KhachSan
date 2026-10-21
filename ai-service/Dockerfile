FROM python:3.11-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 OMP_NUM_THREADS=2 OPENBLAS_NUM_THREADS=2 HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends build-essential libgl1 libglib2.0-0 && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
# Cài đặt PyTorch CPU siêu nhẹ trước (tránh kéo CUDA 2.5GB)
RUN pip install --no-cache-dir --default-timeout=1000 \
    torch torchvision \
    --index-url https://download.pytorch.org/whl/cpu

# Cài đặt các gói còn lại trong requirements
RUN pip install --no-cache-dir --default-timeout=1000 -r requirements.txt \
    && useradd --uid 10001 --create-home ai 
COPY *.py ./
USER 10001
EXPOSE 8082
# Mandatory client certificate verification. Mounted PEM files are deployment secrets.
CMD ["python", "-m", "uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8082", "--workers", "1", "--no-access-log", "--ssl-certfile", "/run/secrets/ai_server_cert", "--ssl-keyfile", "/run/secrets/ai_server_key", "--ssl-ca-certs", "/run/secrets/ai_ca", "--ssl-cert-reqs", "2"]
