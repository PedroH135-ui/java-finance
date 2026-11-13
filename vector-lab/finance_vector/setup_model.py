import argparse
from pathlib import Path
from .model import download_model, LocalEmbeddings

parser = argparse.ArgumentParser()
parser.add_argument('--root', type=Path, required=True)
args = parser.parse_args()
destination = args.root / '.tools/vector-rag/model'
if not (destination / 'model_optimized.onnx').exists():
    download_model(destination)
embeddings = LocalEmbeddings(destination)
assert len(embeddings.embed_query('descricao ficticia')) == 384
print('Modelo local e checksum verificados; embedding de 384 dimensoes gerado.')
