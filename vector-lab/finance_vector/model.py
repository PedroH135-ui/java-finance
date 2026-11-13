import os
os.environ['LANGSMITH_TRACING'] = 'false'
os.environ['LANGCHAIN_TRACING_V2'] = 'false'
os.environ['HF_HUB_DISABLE_TELEMETRY'] = '1'

from pathlib import Path
from fastembed import TextEmbedding
from langchain_core.embeddings import Embeddings
from finance_ai.pipeline import normalize

MODEL = 'sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2'
REPOSITORY = 'Qdrant/paraphrase-multilingual-MiniLM-L12-v2-onnx-Q'
REVISION = 'faf4aa4225822f3bc6376869cb1164e8e3feedd0'
DIMENSIONS = 384
MODEL_KEY = f'{MODEL}@{REVISION}:normalize-v1'
WEIGHTS_SHA256 = '634d0f66c29dc934c8fa72b8a4fe91dd4d420a22f1d82a241058d4316e659a99'


def download_model(destination: Path):
    """Only explicit setup downloads public artifacts. No inference API or financial input."""
    from huggingface_hub import snapshot_download
    return Path(snapshot_download(repo_id=REPOSITORY, revision=REVISION, token=False,
        allow_patterns=['*.json', '*.onnx'], local_dir=str(destination)))


class LocalEmbeddings(Embeddings):
    def __init__(self, model_path: Path):
        import hashlib
        if not (model_path / 'model_optimized.onnx').is_file():
            raise RuntimeError('Download the pinned local ONNX model before inference.')
        if hashlib.sha256((model_path / 'model_optimized.onnx').read_bytes()).hexdigest() != WEIGHTS_SHA256:
            raise RuntimeError('Pinned embedding weights checksum mismatch.')
        self.model = TextEmbedding(model_name=MODEL, specific_model_path=str(model_path),
            local_files_only=True, threads=2, providers=['CPUExecutionProvider'])
        self.cache = {}
        self.encoded_texts = 0

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        normalized = [normalize(text) for text in texts]
        missing = list(dict.fromkeys(text for text in normalized if text not in self.cache))
        if missing:
            for text, vector in zip(missing, self.model.embed(missing, batch_size=8)):
                values = vector.tolist()
                if len(values) != DIMENSIONS:
                    raise RuntimeError('Unexpected embedding dimensions.')
                self.cache[text] = values
            self.encoded_texts += len(missing)
        return [list(self.cache[text]) for text in normalized]

    def embed_query(self, text: str) -> list[float]:
        return self.embed_documents([text])[0]
