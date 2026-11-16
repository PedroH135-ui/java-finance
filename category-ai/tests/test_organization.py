from finance_ai.pipeline import Request,Category,Example,ModelChoice,build_pipeline
from finance_ai.planning import PlanningRequest,GoalState,planning_chain

def request(party='BUSINESS',history=None):
 return Request(description='Transferência enviada pelo PIX ACME LTDA',counterpartyType=party,categories=[Category(id='food',name='Restaurantes e delivery'),Category(id='pix',name='PIX')],history=history or [])

def test_three_paths_control_model_cost_and_abstain():
 calls=[]
 def model(payload):calls.append(payload);return ModelChoice(category_id='food')
 pipeline=build_pipeline(model,provider='groq')
 person=pipeline.invoke(request('PERSON'));assert person.category_id=='pix' and not calls
 history=pipeline.invoke(request(history=[Example(description=request().description,categoryId='food')]));assert history.method=='HISTORY' and not calls
 business=pipeline.invoke(request());assert business.category_id=='food' and business.model_used and len(calls)==1
 calls.clear()
 def none(payload):calls.append(payload);return ModelChoice()
 unknown=build_pipeline(none).invoke(request());assert unknown.category_id is None and len(calls)==1

def test_confident_rag_does_not_call_llm_and_unknown_party_is_not_guessed():
 pipeline=build_pipeline(lambda payload:(_ for _ in ()).throw(AssertionError('LLM must not run')))
 q=request()
 result=pipeline.last.invoke({'request':q,'examples':[{'description':'comida fictícia','categoryId':'food','score':0.1,'similarity':0.94},{'description':'outra fictícia','categoryId':'food','score':0.1,'similarity':0.91}]})
 assert result.method=='RAG' and result.category_id=='food' and not result.model_used
 assert pipeline.invoke(request('UNKNOWN')).category_id is None

def test_goal_conversation_is_langchain_without_llm_and_keeps_amount_and_deadline_separate():
 first=planning_chain.invoke(PlanningRequest(message='Quero viajar e guardar 3.000'))
 assert first.target=='3000' and first.months is None and first.title=='Viagem' and not first.modelUsed
 second=planning_chain.invoke(PlanningRequest(message='em 6 meses',state=GoalState(**{k:v for k,v in first.model_dump().items() if k in GoalState.model_fields})))
 assert second.target=='3000' and second.months==6
 third=planning_chain.invoke(PlanningRequest(message='já tenho 600 reservado',state=GoalState(target='3000',months=6,title='Viagem')))
 assert third.target=='3000' and third.saved=='600' and third.months==6

def test_planning_endpoint_requires_service_auth_and_rejects_extra_context(monkeypatch):
 import importlib
 from fastapi.testclient import TestClient
 monkeypatch.setenv('CATEGORY_AI_PROVIDER','none');monkeypatch.setenv('CATEGORY_AI_MODEL','');monkeypatch.setenv('CATEGORY_AI_TOKEN','synthetic-planning-token-for-tests-only-0000')
 import finance_ai.app as module
 module=importlib.reload(module)
 with TestClient(module.app) as client:
  assert client.post('/planning/interpret',json={'message':'Guardar 3000'}).status_code==401
  headers={'Authorization':'Bearer synthetic-planning-token-for-tests-only-0000'}
  result=client.post('/planning/interpret',headers=headers,json={'message':'Quero viajar e guardar 3000 em 6 meses'})
  assert result.status_code==200 and result.json()['target']=='3000' and not result.json()['modelUsed']
  assert client.post('/planning/interpret',headers=headers,json={'message':'Teste','bankAccount':'not-accepted'}).status_code==422
