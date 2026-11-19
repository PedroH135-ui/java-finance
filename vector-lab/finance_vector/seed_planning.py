"""Fresh fictional planning user, only in the already guarded loopback review DB."""
import argparse,json,subprocess
from pathlib import Path
from uuid import UUID,uuid4
import psycopg

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--root',type=Path,required=True);args=parser.parse_args()
    private=args.root/'.tools/local-review';cfg=json.loads((private/'config.json').read_text(encoding='utf-8-sig'))
    if cfg.get('database')!='java_finance_review' or cfg.get('dbUser')!='review_owner' or not cfg.get('demoSeeded'):raise RuntimeError('Synthetic database guard failed.')
    owner=UUID(cfg['ownerId'])
    label=subprocess.run(['docker','inspect','--format','{{index .Config.Labels "java-finance.local-review"}}','java-finance-review-db'],capture_output=True,text=True,check=True).stdout.strip()
    ports=json.loads(subprocess.run(['docker','inspect','--format','{{json .NetworkSettings.Ports}}','java-finance-review-db'],capture_output=True,text=True,check=True).stdout)
    if label!='true' or ports.get('5432/tcp')!=[{'HostIp':'127.0.0.1','HostPort':'55434'}]:raise RuntimeError('Loopback container guard failed.')
    user=uuid4();email='planning-income-'+str(user)+'@example.test'
    with psycopg.connect(host='127.0.0.1',port=55434,dbname='java_finance_review',user='review_owner',password=cfg['dbPassword'],connect_timeout=5) as conn:
        if conn.execute('SELECT current_database(),current_user').fetchone()!=('java_finance_review','review_owner'):raise RuntimeError('Unexpected database.')
        if conn.execute('SELECT email FROM app_user WHERE id=%s',(owner,)).fetchone()!=('demo@javafinance.local',):raise RuntimeError('Synthetic owner guard failed.')
        # Copy only the known fictional demo login, entirely inside Postgres; no hash leaves the DB.
        conn.execute("INSERT INTO app_user(id,email,display_name,password_hash,created_at) SELECT %s,%s,'Planejamento fictício',password_hash,now() FROM app_user WHERE id=%s AND email='demo@javafinance.local'",(user,email,owner))
        for name,kind in [('Salário','INCOME'),('Mercado','EXPENSE')]:
            conn.execute('INSERT INTO category(id,owner_id,name,kind,essential) VALUES(%s,%s,%s,%s,false)',(uuid4(),user,name,kind))
        conn.execute('INSERT INTO category_automation_preference(owner_id,enabled) VALUES(%s,true)',(user,))
        for table in ('account','movement','posting','bank_entry'):
            if conn.execute('SELECT count(*) FROM '+table+' WHERE owner_id=%s',(user,)).fetchone()[0]!=0:raise RuntimeError('Synthetic user has financial rows.')
    (private/'planning-income-fixture.json').write_text(json.dumps({'ownerId':str(user),'email':email,'syntheticOnly':True},indent=2),encoding='utf-8')
    print('Fresh fictitious planning user prepared. No account, movement, posting or bank record created. Registration settings unchanged.')

if __name__=='__main__':main()
