"""Vercel invokes this private subscriber even with no browser or local computer running."""
import os
from datetime import timedelta
from urllib.parse import urlsplit
from uuid import UUID
import httpx
from vercel.queue import subscribe, send
from vercel.oidc.aio import get_vercel_oidc_token


@subscribe(topic='category-automation',retry_after=timedelta(seconds=60),max_concurrency=1,max_attempts=10)
async def categories(message):
    # No descriptions, amounts, credentials, arbitrary URL or model output travel in the queue.
    if set(message)!={'owner'}:raise ValueError('Invalid queue message.')
    owner=str(UUID(message['owner']))
    base=os.environ.get('CATEGORY_JAVA_URL','')
    uri=urlsplit(base)
    if (uri.scheme!='https' or uri.hostname!=os.environ.get('CATEGORY_JAVA_ALLOWED_HOST')
        or not uri.hostname.endswith('.vercel.app') or uri.path not in {'','/'}
        or uri.username or uri.password or uri.query or uri.fragment or uri.port not in {None,443}):
        raise RuntimeError('Java callback destination is not configured.')
    token=os.environ.get('CATEGORY_AI_JOB_TOKEN','')
    if len(token)<32:raise RuntimeError('Job token missing.')
    headers={'Authorization':'Bearer '+token}
    # The SDK reads the current subscriber request identity; a build-time env token would expire.
    headers['x-vercel-trusted-oidc-idp-token']=await get_vercel_oidc_token()
    async with httpx.AsyncClient(timeout=90,trust_env=False,follow_redirects=False) as client:
        response=await client.post(base.rstrip('/')+'/api/banking/jobs/categories',json={'owner':owner},headers=headers)
    if response.status_code!=200:raise RuntimeError('Category batch must be retried.')
    if response.json().get('more'):
        await send('category-automation',{'owner':owner})
