"""Goal interpretation with LangChain; deterministic, no provider call or bank values."""
import re
import unicodedata
from decimal import Decimal
from typing import Literal
from pydantic import BaseModel,ConfigDict,Field
from langchain_core.runnables import RunnableLambda

class GoalState(BaseModel):
    model_config=ConfigDict(extra='forbid')
    target:str|None=Field(default=None,max_length=24)
    saved:str|None=Field(default=None,max_length=24)
    months:int|None=Field(default=None,ge=1,le=120)
    title:str=Field(default='Minha meta',max_length=100)

class PlanningRequest(BaseModel):
    model_config=ConfigDict(extra='forbid')
    message:str=Field(min_length=1,max_length=600)
    state:GoalState=Field(default_factory=GoalState)

class GoalIntent(GoalState):
    action:Literal['GOAL','PURCHASE','SUMMARY']='GOAL'
    modelUsed:bool=False
    method:str='LANGCHAIN_RULES'

def amount(text):
    if re.search(r'-\s*\d',text):return None
    match=re.search(r'(?<!\w)(\d[\d.,]*)(?:\s*(mil))?',text)
    if not match:return None
    raw=match[1]
    if ',' in raw:raw=raw.replace('.','').replace(',','.')
    elif re.fullmatch(r'\d{1,3}(\.\d{3})+',raw):raw=raw.replace('.','')
    try:
        value=Decimal(raw)*(1000 if match[2] else 1)
        return format(value,'f') if value.is_finite() and 0<=value<=Decimal('999999999999.99') else None
    except Exception:return None

def interpret(request:PlanningRequest):
    text=''.join(c for c in unicodedata.normalize('NFD',request.message.lower()) if unicodedata.category(c)!='Mn');data=request.state.model_dump()
    months=re.search(r'\b(\d{1,3})\s*mes(?:es)?\b',text)
    if months and 1<=int(months[1])<=120:data['months']=int(months[1])
    cleaned=re.sub(r'\b\d{1,3}\s*mes(?:es)?\b','',text)
    value=amount(cleaned)
    action='PURCHASE' if any(t in text for t in ('comprar','posso gastar','cabe no orçamento')) else 'SUMMARY' if any(t in text for t in ('meu resumo','meus gastos','como estou')) else 'GOAL'
    if value is not None:
        if any(t in text for t in ('já tenho','ja tenho','já guardei','ja guardei','reservado')):data['saved']=value
        else:data['target']=value
    if 'viaj' in text:data['title']='Viagem'
    return GoalIntent(**data,action=action)

planning_chain=RunnableLambda(lambda request:request,name='ler_objetivo')|RunnableLambda(interpret,name='interpretar_sem_llm')

def install_planning(app,auth):
    from fastapi import Depends
    @app.post('/planning/interpret',dependencies=[Depends(auth)],response_model=GoalIntent)
    def goal(payload:PlanningRequest):return planning_chain.invoke(payload)
