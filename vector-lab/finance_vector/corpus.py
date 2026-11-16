from langchain_core.documents import Document
from uuid import NAMESPACE_URL, uuid5
from .model import MODEL_KEY

FIXTURE = 'synthetic-vector-v1'
OWNER = 'perfil_ficticio_a'
CATEGORIES = [{'id': key, 'name': name} for key, name in (
    ('food', 'Alimentação'), ('delivery', 'Delivery'), ('subscriptions', 'Assinaturas'),
    ('transport', 'Transporte'), ('health', 'Saúde'), ('housing', 'Moradia'))]
EXAMPLES = [
    ('supermercado compra de alimentos', 'food'),
    ('feira de frutas e verduras', 'food'),
    ('padaria pao e leite', 'food'),
    ('pedido de pizza entregue em casa', 'delivery'),
    ('aplicativo de entrega de comida', 'delivery'),
    ('assinatura mensal de filmes e series', 'subscriptions'),
    ('plano mensal de musica online', 'subscriptions'),
    ('corrida de carro por aplicativo', 'transport'),
    ('passagem de onibus urbano', 'transport'),
    ('remedios comprados na farmacia', 'health'),
    ('consulta medica na clinica', 'health'),
    ('conta de energia eletrica', 'housing'),
    ('pagamento da conta de agua', 'housing'),
    ('loja exemplo', 'food'),
    ('loja exemplo', 'delivery'),
]
# Expectations are fixed before looking at vector results. No category label is embedded.
QUERIES = [
    ('supermercado compra de alimentos', 'food', 'Descrição conhecida'),
    ('hortifruti legumes frescos', 'food', 'Sinônimos de alimentação'),
    ('pizza em domicilio', 'delivery', 'Sinônimos de delivery'),
    ('mensalidade plataforma streaming de video', 'subscriptions', 'Streaming sem marca'),
    ('pagamento streaming musical', 'subscriptions', 'Música sem marca'),
    ('medicamentos na drogaria', 'health', 'Sinônimos de saúde'),
    ('viagem de uber', 'transport', 'Transporte'),
    ('conta de luz residencial', 'housing', 'Sinônimos de moradia'),
    ('pix desconhecido', None, 'Informação insuficiente'),
    ('pix enviado joao silva', None, 'Nome fictício sem finalidade'),
    ('transferencia entre minhas contas', None, 'Natureza financeira ambígua'),
    ('loja exemplo', None, 'Histórico conflitante'),
]


def documents():
    rows = [(OWNER, text, category, 'EXPENSE', True) for text, category in EXAMPLES]
    rows += [
        ('perfil_ficticio_b', 'pizza em domicilio', 'health', 'EXPENSE', True),
        (OWNER, 'pizza em domicilio', 'health', 'INCOME', True),
        (OWNER, 'pizza em domicilio', 'health', 'EXPENSE', False),
    ]
    return [Document(id=str(uuid5(NAMESPACE_URL, f'{FIXTURE}|{MODEL_KEY}|{owner}|{text}|{category}|{direction}|{active}')),
        page_content=text, metadata={'owner_id': owner, 'category_id': category,
            'direction': direction, 'active': active, 'model_key': MODEL_KEY, 'fixture': FIXTURE})
        for owner, text, category, direction, active in rows]
