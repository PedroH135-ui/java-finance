"""One-time fictitious fixture. Only the labelled local review database is accepted.

Run after start-review-local.ps1, using .tools/category-ai-venv/Scripts/python.exe.
This script never loads production environment files or banking credentials.
"""
import http.cookiejar
import argparse
import json
from pathlib import Path
import subprocess
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / ".tools" / "local-review"
BASE = "http://127.0.0.1:8082"
CONTAINER = "java-finance-review-db"
EMAIL = "demo@javafinance.local"
PASSWORD = "DemoLocal123!"  # Public, fictitious local account only.


def command(*args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, text=True, encoding="utf-8", **kwargs).stdout.strip()


def sql(statement):
    return command("docker", "exec", "-i", CONTAINER, "psql", "-U", "review_owner",
                   "-d", "java_finance_review", "-v", "ON_ERROR_STOP=1", "-At", input=statement)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--llm-examples", action="store_true", help="Adiciona dois registros fictícios de efeito líquido zero para testar o LLM.")
    args = parser.parse_args()
    config_path = PRIVATE / "config.json"
    config = json.loads(config_path.read_text(encoding="utf-8-sig"))
    if config["database"] != "java_finance_review" or config["dbUser"] != "review_owner":
        raise RuntimeError("Configuração diferente do banco local de demonstração.")
    if command("docker", "inspect", "--format", '{{index .Config.Labels "java-finance.local-review"}}', CONTAINER) != "true":
        raise RuntimeError("Container não identificado como demonstração local.")
    ports = json.loads(command("docker", "inspect", "--format", '{{json .NetworkSettings.Ports}}', CONTAINER))
    if ports.get("5432/tcp") != [{"HostIp": "127.0.0.1", "HostPort": "55434"}]:
        raise RuntimeError("A porta do banco não corresponde à demonstração local.")
    if config.get("demoSeeded"):
        if args.llm_examples:
            add_llm_examples(config)
            return
        print("Demonstração já preparada; os dados existentes foram preservados.")
        return
    # Refuse to mix fixtures with existing user work, including partially seeded data.
    if sql("SELECT count(*) FROM app_user;") != "0":
        raise RuntimeError("Banco já contém usuários; nenhum dado será sobrescrito.")
    cookies = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies))

    def api(path, data=None, method=None, form=False):
        headers = {}
        if data is not None:
            csrf = api("/auth/csrf")
            headers[csrf["headerName"]] = csrf["token"]
            headers["Content-Type"] = "application/x-www-form-urlencoded" if form else "application/json"
            data = (urllib.parse.urlencode(data) if form else json.dumps(data)).encode()
        request = urllib.request.Request(BASE + "/api" + path, data=data, headers=headers, method=method)
        with opener.open(request, timeout=30) as response:
            body = response.read()
            return json.loads(body) if body else None

    user = api("/auth/register", {"email": EMAIL, "name": "Demonstração local", "password": PASSWORD})
    api("/auth/login", {"username": EMAIL, "password": PASSWORD}, form=True)
    owner = str(uuid.UUID(user["id"]))
    categories = {}
    for name in ["Alimentação", "Saúde", "Assinaturas", "Transporte", "Moradia", "Outros gastos", "Salário"]:
        categories[name] = api("/categories", {"name": name, "kind": "INCOME" if name == "Salário" else "EXPENSE", "essential": name != "Assinaturas"})["id"]
    accounts = []
    for name, kind, opening in [("Nubank — DEMONSTRAÇÃO", "CHECKING", "1000.00"), ("Banco B — DEMONSTRAÇÃO", "CHECKING", "100.00"), ("Carteira — DEMONSTRAÇÃO", "WALLET", "100.00")]:
        accounts.append(api("/accounts", {"name": name, "institution": "Instituição fictícia", "kind": kind, "openingBalance": opening, "openingDate": "2026-11-01"})["id"])
    for description, amount, category, status, day in [("Ônibus — teste", "20.00", "Transporte", "CONFIRMED", 7), ("Aluguel planejado — teste", "150.00", "Moradia", "PLANNED", 20)]:
        date = f"2026-11-{day:02}"
        api("/movements", {"requestKey": str(uuid.uuid4()), "description": description, "amount": amount, "kind": "EXPENSE", "status": status, "accountId": accounts[2], "destinationId": None, "categoryId": categories[category], "operationDate": date, "competence": "2026-11-01", "dueDate": date, "settledDate": date if status == "CONFIRMED" else None, "notes": "Dados fictícios para validação local."})
    links = [str(uuid.uuid4()), str(uuid.uuid4())]
    statements = ["BEGIN;"]
    for i, balance in enumerate(["3025.00", "600.00"]):
        statements.append(f"INSERT INTO bank_link(id,owner_id,item_id,external_account_id,account_id,name,import_from,reported_balance,last_checked_at,provider_updated_at) VALUES('{links[i]}','{owner}','{uuid.uuid4()}','{uuid.uuid4()}','{accounts[i]}','Conexão simulada — sem acesso bancário','2026-11-02',{balance},now(),now());")
    fixtures = [
        ("salary", "Salário empresa fictícia", "3000", 5, "CREDIT", "POSTED", False, 0),
        ("market_history", "Mercado Alfa", "250", 6, "DEBIT", "POSTED", False, 0),
        ("market", "Mercado Alfa", "75", 10, "DEBIT", "POSTED", False, 0),
        ("pharmacy", "Farmácia Beta", "50", 11, "DEBIT", "POSTED", False, 0),
        ("streaming", "Netflix", "40", 12, "DEBIT", "POSTED", False, 0),
        ("unknown", "PIX desconhecido", "60", 13, "DEBIT", "POSTED", False, 0),
        ("transfer_out", "Transferência entre minhas contas — teste", "500", 9, "DEBIT", "POSTED", False, 0),
        ("duplicate", "Cobrança duplicada — teste oculto", "30", 8, "DEBIT", "POSTED", True, 0),
        ("refund", "Estorno duplicado — teste oculto", "30", 8, "CREDIT", "POSTED", True, 0),
        ("pending", "Compra pendente — teste", "150", 13, "DEBIT", "PENDING", False, 0),
        ("transfer_in", "Transferência entre minhas contas — teste", "500", 9, "CREDIT", "POSTED", False, 1),
    ]
    entries = {}
    for key, description, amount, day, direction, status, hidden, link in fixtures:
        entries[key] = str(uuid.uuid4())
        statements.append(f"INSERT INTO bank_entry(id,owner_id,link_id,external_id,description,amount,date,direction,provider_status,hidden) VALUES('{entries[key]}','{owner}','{links[link]}','demo-{key}','{description}',{amount},'2026-11-{day:02}','{direction}','{status}',{str(hidden).lower()});")
    statements.append("COMMIT;")
    sql("\n".join(statements))
    for key, category in [("salary", "Salário"), ("market_history", "Alimentação")]:
        api(f"/banking/entries/{entries[key]}/category", {"version": 0, "categoryId": categories[category]})
    for category, amount in [("Alimentação", "400"), ("Saúde", "150"), ("Transporte", "100"), ("Assinaturas", "100"), ("Moradia", "1000")]:
        api(f"/budgets/2026-11/categories/{categories[category]}", {"amount": amount, "version": None}, method="PUT")
    config.update(ownerId=owner, demoSeeded=True)
    config_path.write_text(json.dumps(config, indent=2), encoding="utf-8")
    (PRIVATE / "demo-fixture.json").write_text(json.dumps({"ownerId": owner, "accounts": accounts, "categories": categories, "entries": entries}, indent=2, ensure_ascii=False), encoding="utf-8")
    print("Cenário fictício criado: 3 contas, 11 registros bancários simulados, 2 despesas manuais e 5 orçamentos. Nenhuma consulta bancária realizada.")
    if args.llm_examples:
        add_llm_examples(config)


def add_llm_examples(config):
    """Optional, idempotent examples; balanced debit/credit preserves demo balances."""
    owner = str(uuid.UUID(config["ownerId"]))
    fixture_path = PRIVATE / "demo-fixture.json"
    fixture = json.loads(fixture_path.read_text(encoding="utf-8"))
    account = str(uuid.UUID(fixture["accounts"][0]))
    if sql(f"SELECT email FROM app_user WHERE id='{owner}';") != EMAIL:
        raise RuntimeError("Perfil diferente da demonstração; nenhum exemplo será adicionado.")
    link = str(uuid.UUID(sql(f"SELECT id FROM bank_link WHERE owner_id='{owner}' AND account_id='{account}';")))
    statements = ["BEGIN;"]
    for key, description, direction in [("llm_restaurant", "Restaurante Sabor da Vila", "DEBIT"), ("llm_refund", "Estorno compra fictícia de teste", "CREDIT")]:
        statements.append(f"INSERT INTO bank_entry(id,owner_id,link_id,external_id,description,amount,date,direction,provider_status) VALUES('{uuid.uuid4()}','{owner}','{link}','demo-{key}','{description}',35,'2026-11-13','{direction}','POSTED') ON CONFLICT(link_id,external_id) DO NOTHING;")
    statements.append("COMMIT;")
    sql("\n".join(statements))
    for key in ["llm_restaurant", "llm_refund"]:
        fixture["entries"][key] = str(uuid.UUID(sql(f"SELECT id FROM bank_entry WHERE link_id='{link}' AND external_id='demo-{key}';")))
    fixture_path.write_text(json.dumps(fixture, indent=2, ensure_ascii=False), encoding="utf-8")
    print("Exemplos LLM preparados: restaurante R$ 35 e estorno R$ 35. Efeito líquido zero, sem alterar snapshots de saldo ou escolhas existentes.")


if __name__ == "__main__":
    main()
