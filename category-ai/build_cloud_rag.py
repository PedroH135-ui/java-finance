"""Explicit build-time public artifact download; never executed on an inference request."""
import os
from pathlib import Path

if os.environ.get('CATEGORY_AI_CLOUD_RAG')=='true':
    from finance_ai.embeddings import download_model, LocalEmbeddings
    destination=Path(__file__).resolve().parent/'models'
    download_model(destination)
    LocalEmbeddings(destination)  # Refuse a build whose pinned checksum or dimensions are wrong.
