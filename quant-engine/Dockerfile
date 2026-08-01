FROM public.ecr.aws/docker/library/python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

RUN sed -i 's|http://deb.debian.org|https://mirrors.tuna.tsinghua.edu.cn|g' /etc/apt/sources.list.d/debian.sources \
    && apt-get update \
    && apt-get install --yes --no-install-recommends libgomp1 tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system quantfund \
    && useradd --system --gid quantfund --create-home quantfund

WORKDIR /app
COPY pyproject.toml requirements.txt ./
RUN pip install --upgrade pip \
    && pip install -r requirements.txt

COPY app ./app
COPY models ./models
RUN mkdir -p /app/.cache \
    && chown -R quantfund:quantfund /app

USER quantfund
EXPOSE 8091

CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8091"]
