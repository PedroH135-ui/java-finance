"""Explicit Vercel cloud RAG entrypoint; legacy deployment remains app.py."""
from fastapi import FastAPI
from finance_ai.vector_app import create_app

app: FastAPI = create_app()
