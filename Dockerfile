# Use official lightweight Python 3.11 slim image
FROM python:3.11-slim

# Prevent Python from writing .pyc files and enable unbuffered logging
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PORT=10000

# Set working directory
WORKDIR /app

# Install system dependencies required for compilation and Postgres support
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libpq-dev \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements file first for optimal layer caching
COPY requirements.txt /app/requirements.txt

# Upgrade pip and install Python packages
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

# Copy application files
COPY . /app/

# Create non-root user for security
RUN adduser --disabled-password --gecos "" appuser && \
    chown -R appuser:appuser /app
USER appuser

# Expose target service port
EXPOSE 10000

# Health check to ensure service availability
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:10000/health || exit 1

# Entry point command for production server
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "10000"]

