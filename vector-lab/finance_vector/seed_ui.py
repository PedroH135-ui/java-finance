"""Add idempotent, balanced fixtures only to the verified existing local demo."""
import argparse
import json
import subprocess
from pathlib import Path
from uuid import UUID, NAMESPACE_URL, uuid5
import psycopg
from .corpus import CATEGORIES, EXAMPLES

TARGETS = [('pizza','Pizza em domicílio'),('music','Pagamento streaming musical'),
    ('health','Medicamentos na drogaria'),('housing','Conta de luz residencial')]


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--root',type=Path,required=True)
    parser.add_argument('--automatic-test-id',type=UUID)
    args=parser.parse_args();root=args.root
    private=root/'.tools/local-review'
    cfg=json.loads((private/'config.json').read_text(encoding='utf-8-sig'))
    if cfg.get('database')!='java_finance_review' or cfg.get('dbUser')!='review_owner' or not cfg.get('demoSeeded'):
        raise RuntimeError('Only the already-seeded synthetic review database is allowed.')
    owner=UUID(cfg['ownerId'])
    label=subprocess.run(['docker','inspect','--format','{{index .Config.Labels "java-finance.local-review"}}','java-finance-review-db'],check=True,capture_output=True,text=True).stdout.strip()
    ports=json.loads(subprocess.run(['docker','inspect','--format','{{json .NetworkSettings.Ports}}','java-finance-review-db'],check=True,capture_output=True,text=True).stdout)
    if label!='true' or ports.get('5432/tcp')!=[{'HostIp':'127.0.0.1','HostPort':'55434'}]:
        raise RuntimeError('The container does not match the isolated review environment.')
    fixture=json.loads((private/'demo-fixture.json').read_text(encoding='utf-8-sig'))
    account=UUID(fixture['accounts'][0])
    with psycopg.connect(host='127.0.0.1',port=55434,dbname='java_finance_review',user='review_owner',password=cfg['dbPassword'],connect_timeout=5) as conn:
        if conn.execute('SELECT current_database(),current_user').fetchone()!=('java_finance_review','review_owner'):
            raise RuntimeError('Unexpected demo database.')
        if conn.execute('SELECT email FROM app_user WHERE id=%s',(owner,)).fetchone()!=('demo@javafinance.local',):
            raise RuntimeError('The owner is not the known fictitious profile.')
        def invariants():
            return (conn.execute('SELECT count(*) FROM movement WHERE owner_id=%s',(owner,)).fetchone(),
                conn.execute('SELECT count(*) FROM posting WHERE owner_id=%s',(owner,)).fetchone(),
                conn.execute('SELECT id,reported_balance FROM bank_link WHERE owner_id=%s ORDER BY id',(owner,)).fetchall())
        before=invariants()
        category_ids={}
        for category in CATEGORIES:
            existing=conn.execute("SELECT id FROM category WHERE owner_id=%s AND lower(name)=lower(%s) AND kind='EXPENSE' AND NOT archived",(owner,category['name'])).fetchone()
            cid=existing[0] if existing else uuid5(NAMESPACE_URL,f'vector-ui-category|{owner}|{category["id"]}')
            if not existing:
                conn.execute("INSERT INTO category(id,owner_id,name,kind,essential) VALUES(%s,%s,%s,'EXPENSE',false)",(cid,owner,category['name']))
            category_ids[category['id']]=cid
        link=conn.execute('SELECT id FROM bank_link WHERE owner_id=%s AND account_id=%s ORDER BY id LIMIT 1',(owner,account)).fetchone()[0]
        def add(key,description,amount,direction,day,category=None):
            eid=uuid5(NAMESPACE_URL,f'vector-ui-v1|{owner}|{key}')
            conn.execute('''INSERT INTO bank_entry(id,owner_id,link_id,external_id,description,amount,date,direction,provider_status,category_id,category_description,category_direction)
                VALUES(%s,%s,%s,%s,%s,%s,%s,%s,'POSTED',%s,%s,%s) ON CONFLICT(link_id,external_id) DO NOTHING''',
                (eid,owner,link,'vector-ui-'+key,description,amount,day,direction,category,
                    description if category else None,direction if category else None))
            return str(eid)
        for i,(description,category) in enumerate(EXAMPLES):
            add(f'history-{i}',description,'1.00','DEBIT','2026-10-15',category_ids[category])
        add('history-offset','Recomposição fictícia do histórico vetorial',str(len(EXAMPLES)),'CREDIT','2026-10-15')
        targets={key:add('target-'+key,description,'10.00','DEBIT','2026-11-14') for key,description in TARGETS}
        add('targets-offset','Recomposição fictícia do cenário vetorial',str(10*len(TARGETS)),'CREDIT','2026-11-14')
        automatic=None
        if args.automatic_test_id:
            key=str(args.automatic_test_id)
            description='Pizza delivery automática fictícia '+key[:8]
            automatic={'id':add('automatic-'+key,description,'10.00','DEBIT','2026-11-16'),'description':description,
                'deliveryCategoryId':str(category_ids['delivery']),'otherCategoryId':str(category_ids['food'])}
            add('automatic-offset-'+key,'Recomposição fictícia do teste automático '+key[:8],'10.00','CREDIT','2026-11-16')
        if invariants()!=before: raise RuntimeError('Financial invariants changed; the fixture transaction will roll back.')
    (private/'vector-ui-fixture.json').write_text(json.dumps({'ownerId':str(owner),'targets':targets,'categories':{key:str(value) for key,value in category_ids.items()}},indent=2),encoding='utf-8')
    if automatic:(private/'automatic-category-fixture.json').write_text(json.dumps(automatic,indent=2),encoding='utf-8')
    print('Four fictitious records and classified synthetic history are ready; existing classifications preserved. No movements/postings or bank balances changed.')


if __name__=='__main__':main()
