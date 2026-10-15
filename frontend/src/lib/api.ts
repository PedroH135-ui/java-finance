export class ApiError extends Error {
  constructor(public status: number, message: string, public fields: Record<string, string> = {}) { super(message); }
}
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.method && !['GET', 'HEAD'].includes(options.method)) {
    const csrfResponse = await fetch('/api/auth/csrf', { credentials: 'include' });
    if (!csrfResponse.ok) throw new ApiError(csrfResponse.status, 'Não foi possível iniciar uma sessão segura. Atualize a página.');
    const csrf: { token: string; headerName: string } = await csrfResponse.json();
    headers.set(csrf.headerName, csrf.token);
  }
  if (options.body && typeof options.body === 'string' && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const response = await fetch('/api' + path, { ...options, headers, credentials: 'include' });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({ message: 'Falha de comunicação. Tente novamente.' }));
    throw new ApiError(response.status, problem.message ?? 'Não foi possível concluir.', problem.fields);
  }
  return response.status === 204 || response.headers.get('content-length') === '0' ? undefined as T :
    await response.text().then(text => text ? JSON.parse(text) as T : undefined as T);
}
export const json = (body: unknown) => JSON.stringify(body);

