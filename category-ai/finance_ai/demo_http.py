"""Synthetic HTTP demonstration with an ephemeral token; no database or bank access."""
import json
import os
import secrets
import socket
import threading
import time
import urllib.request


def main():
    os.environ.pop("CATEGORY_AI_MODEL", None)
    token = secrets.token_urlsafe(32)
    os.environ["CATEGORY_AI_TOKEN"] = token
    import uvicorn
    from .app import app

    listener = socket.socket()
    listener.bind(("127.0.0.1", 0))
    listener.listen(16)
    port = listener.getsockname()[1]
    server = uvicorn.Server(uvicorn.Config(app, log_level="warning", access_log=False))
    worker = threading.Thread(target=lambda: server.run(sockets=[listener]), daemon=True)
    worker.start()
    try:
        for _ in range(250):
            if server.started: break
            time.sleep(0.02)
        if not server.started: raise RuntimeError("Local demo server did not start")
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        categories = [{"id": "food", "name": "Alimentação"}, {"id": "transport", "name": "Transporte"}]
        cases = [("Mercado Alfa", []), ("Loja Exemplo", [{"description": "Loja Exemplo", "categoryId": "transport"}]), ("Pix desconhecido", [])]
        for description, history in cases:
            request = urllib.request.Request(f"http://127.0.0.1:{port}/suggest",
                data=json.dumps({"description": description, "categories": categories, "history": history}).encode(),
                headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"})
            with opener.open(request, timeout=10) as response:
                result = json.load(response)
            print(json.dumps({"example": description, "http": 200, "suggestion": result}, ensure_ascii=False))
    finally:
        server.should_exit = True
        worker.join(timeout=5)
        listener.close()
        os.environ.pop("CATEGORY_AI_TOKEN", None)


if __name__ == "__main__": main()
