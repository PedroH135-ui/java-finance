import { useEffect, useState } from 'react';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, Typography } from '@mui/material';
interface InstallEvent extends Event { prompt(): Promise<void>; userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }> }
let pendingInstall: InstallEvent | null = null;
// Capture at startup, before settings is opened.
window.addEventListener('beforeinstallprompt', event => {
  event.preventDefault(); pendingInstall = event as InstallEvent;
  window.dispatchEvent(new Event('java-finance-install-available'));
});
export function MobileApp() {
  const [prompt, setPrompt] = useState<InstallEvent | null>(pendingInstall);
  const [help, setHelp] = useState(false);
  const [installed, setInstalled] = useState(() => window.matchMedia('(display-mode: standalone)').matches || Boolean((navigator as Navigator & { standalone?: boolean }).standalone));
  useEffect(() => {
    const available = () => { setPrompt(pendingInstall); };
    const done = () => { pendingInstall = null; setInstalled(true); setPrompt(null); };
    window.addEventListener('java-finance-install-available', available);
    window.addEventListener('appinstalled', done);
    return () => { window.removeEventListener('java-finance-install-available', available); window.removeEventListener('appinstalled', done); };
  }, []);
  async function install() {
    if (!prompt) { setHelp(true); return; }
    await prompt.prompt();
    const choice = await prompt.userChoice;
    if (choice.outcome === 'accepted') setInstalled(true);
    pendingInstall = null; setPrompt(null);
  }
  return <>
    <Stack spacing={2}><Typography>Abra o Java Finance direto na tela inicial do seu celular, com as mesmas contas e movimentações.</Typography>
      {installed ? <Alert severity="success">Você está usando o aplicativo instalado.</Alert> : <Button variant="contained" onClick={() => void install()}>Instalar aplicativo</Button>}
      <Button onClick={() => setHelp(true)}>Como instalar no celular</Button>
      <Typography variant="body2" color="text.secondary">Uma conexão com a internet é necessária para consultar e salvar seus dados.</Typography>
    </Stack>
    <Dialog open={help} onClose={() => setHelp(false)} aria-labelledby="install-help"><DialogTitle id="install-help">Instalar no celular</DialogTitle><DialogContent>
      <Stack spacing={2}><Typography><strong>Android:</strong> abra o endereço no Chrome, toque no menu ⋮ e escolha “Instalar aplicativo” ou “Adicionar à tela inicial”.</Typography>
        <Typography><strong>iPhone:</strong> abra no Safari, toque em Compartilhar e escolha “Adicionar à Tela de Início”. Ative “Abrir como App” se essa opção aparecer.</Typography>
        <Typography variant="body2">Se você já instalou, abra pelo ícone na tela inicial.</Typography></Stack>
    </DialogContent><DialogActions><Button onClick={() => setHelp(false)}>Entendi</Button></DialogActions></Dialog>
  </>;
}
