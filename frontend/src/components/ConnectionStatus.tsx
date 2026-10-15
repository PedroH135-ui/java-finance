import { useEffect, useState } from 'react';
import { Alert } from '@mui/material';
export function ConnectionStatus() {
  const [online, setOnline] = useState(navigator.onLine);
  useEffect(() => {
    const update = () => setOnline(navigator.onLine);
    window.addEventListener('online', update); window.addEventListener('offline', update);
    return () => { window.removeEventListener('online', update); window.removeEventListener('offline', update); };
  }, []);
  return online ? null : <Alert severity="warning" role="status" sx={{ position: 'sticky', top: 0, zIndex: 1300 }}>Sem conexão. Reconecte-se para consultar e salvar suas movimentações.</Alert>;
}
