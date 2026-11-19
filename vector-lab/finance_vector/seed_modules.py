"""Prepare a fictional review user, never production; copy the demo hash only inside PostgreSQL."""
import argparse,json,subprocess
from pathlib import Path
from uuid import UUID,uuid4
from datetime import date,timedelta
import psycopg

def main():
 p=argparse.ArgumentParser();p.add_argument('--root',type=Path,required=True);args=p.parse_args();private=args.root/'.tools/local-review'
 cfg=json.loads((private/'config.json').read_text(encoding='utf-8-sig'))
 if cfg.get('database')!='java_finance_review' or cfg.get('dbUser')!='review_owner' or not cfg.get('demoSeeded'):raise RuntimeError('Review database guard failed.')
 label=subprocess.run(['docker','inspect','--format','{{index .Config.Labels "java-finance.local-review"}}','java-finance-review-db'],capture_output=True,text=True,check=True).stdout.strip()
 ports=json.loads(subprocess.run(['docker','inspect','--format','{{json .NetworkSettings.Ports}}','java-finance-review-db'],capture_output=True,text=True,check=True).stdout)
 if label!='true' or ports.get('5432/tcp')!=[{'HostIp':'127.0.0.1','HostPort':'55434'}]:raise RuntimeError('Loopback container guard failed.')
 owner,item,external,account,link=[uuid4() for _ in range(5)];email='modules-'+str(owner)+'@example.test';today=date.today()
 with psycopg.connect(host='127.0.0.1',port=55434,dbname='java_finance_review',user='review_owner',password=cfg['dbPassword'],connect_timeout=5) as db:
  if db.execute('SELECT current_database(),current_user').fetchone()!=('java_finance_review','review_owner'):raise RuntimeError('Database identity mismatch.')
  if db.execute('SELECT email FROM app_user WHERE id=%s',(UUID(cfg['ownerId']),)).fetchone()!=('demo@javafinance.local',):raise RuntimeError('Demo owner mismatch.')
  db.execute("INSERT INTO app_user(id,email,display_name,password_hash,created_at) SELECT %s,%s,'Módulos fictícios',password_hash,now() FROM app_user WHERE id=%s",(owner,email,UUID(cfg['ownerId'])))
  for name,kind in [('Salário','INCOME'),('Outras receitas','INCOME'),('Mercado','EXPENSE'),('Delivery','EXPENSE'),('Assinaturas','EXPENSE'),('Lazer','EXPENSE')]:
   db.execute('INSERT INTO category(id,owner_id,name,kind,essential) VALUES(%s,%s,%s,%s,false)',(uuid4(),owner,name,kind))
  db.execute('INSERT INTO category_automation_preference(owner_id,enabled) VALUES(%s,false)',(owner,))
  db.execute("INSERT INTO account(id,owner_id,name,institution,kind,currency,opening_date) VALUES(%s,%s,'Conta de demonstração','Banco fictício','CHECKING','BRL',%s)",(account,owner,today-timedelta(days=10)))
  db.execute("INSERT INTO bank_link(id,owner_id,item_id,external_account_id,account_id,name,import_from,reported_balance) VALUES(%s,%s,%s,%s,%s,'Banco fictício',%s,1000)",(link,owner,item,external,account,today-timedelta(days=5)))
  for text,amount,direction in [('Transferência Recebida|EMPRESA DEMONSTRAÇÃO LTDA',3000,'CREDIT'),('Pagamento de fatura',480,'DEBIT'),('Aplicação RDB',800,'DEBIT')]:
   entry=uuid4();db.execute("INSERT INTO bank_entry(id,owner_id,link_id,external_id,description,amount,date,direction,provider_status) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,'POSTED')",(entry,owner,link,str(entry),text,amount,today,direction))
  if db.execute('SELECT count(*) FROM posting WHERE owner_id=%s',(owner,)).fetchone()[0]:raise RuntimeError('Unexpected fictitious postings.')
 (private/'modules-fixture.json').write_text(json.dumps({'ownerId':str(owner),'email':email,'itemId':str(item),'linkId':str(link),'syntheticOnly':True},indent=2),encoding='utf-8')
 print('Fictitious modules fixture prepared in the guarded local review database; no real user or production access.')

if __name__=='__main__':main()
