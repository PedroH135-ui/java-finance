import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, LinearProgress, Paper, Stack, TextField, Typography } from '@mui/material';
import { NumericFormat } from 'react-number-format';
import { useBlocker } from 'react-router-dom';
export function PageHeader({ title, subtitle, action }: { title: string; subtitle: string; action?: ReactNode }) {
  return <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'stretch', sm: 'center' }} gap={2} mb={3}>
    <Box><Typography variant="h4" component="h1">{title}</Typography><Typography color="text.secondary" mt={0.5}>{subtitle}</Typography></Box>{action}
  </Stack>;
}
export function Empty({ title, children, action }: { title: string; children: ReactNode; action?: ReactNode }) {
  return <Box py={5} px={2} textAlign="center"><Typography variant="h6">{title}</Typography><Typography color="text.secondary" mt={1} mb={2}>{children}</Typography>{action}</Box>;
}
export function Loading() { return <Box py={3} aria-label="Carregando dados"><LinearProgress/><Typography variant="body2" mt={1}>Carregando seus dados…</Typography></Box>; }
export function Failure({ error, retry }: { error: Error; retry?: () => void }) {
  return <Alert severity="error" action={retry ? <Button color="inherit" onClick={retry}>Tentar novamente</Button> : undefined}>{error.message}</Alert>;
}
export function Panel({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return <Paper variant="outlined" sx={{ p: { xs: 2, md: 3 }, minWidth: 0 }}><Typography variant="h6" component="h2">{title}</Typography>{subtitle && <Typography variant="body2" color="text.secondary" mb={2}>{subtitle}</Typography>}{children}</Paper>;
}
export function MoneyInput({ label, value, onChange, error, helperText, signed = false }: {
  label: string; value: string; onChange: (value: string) => void; error?: boolean; helperText?: string; signed?: boolean;
}) {
  return <NumericFormat customInput={TextField} label={label} value={value} valueIsNumericString decimalSeparator="," thousandSeparator="."
    decimalScale={2} allowNegative={signed} prefix="R$ " onValueChange={v => onChange(v.value)} error={error}
    helperText={helperText} slotProps={{ htmlInput: { inputMode: 'decimal' } }} />;
}
export function FormDialog({ title, children, actions, dirty, busy, onClose }: {
  title: string; children: ReactNode; actions: ReactNode; dirty: boolean; busy: boolean; onClose: () => void;
}) {
  const id = useId();
  const [discard, setDiscard] = useState(false);
  const allowClose = useRef(false);
  const blocker = useBlocker(() => dirty && !busy && !allowClose.current);
  useEffect(() => {
    if (!dirty) return;
    const handler = (e: BeforeUnloadEvent) => { e.preventDefault(); };
    window.addEventListener('beforeunload', handler); return () => window.removeEventListener('beforeunload', handler);
  }, [dirty]);
  const closing = () => { if (!busy) { if (dirty) setDiscard(true); else onClose(); } };
  return <>
    <Dialog open onClose={closing} aria-labelledby={id}>
      <DialogTitle id={id}>{title}</DialogTitle><DialogContent dividers>{children}</DialogContent>
      <DialogActions sx={{ p: 2 }}><Button onClick={closing} disabled={busy}>Voltar</Button>{actions}</DialogActions>
    </Dialog>
    <Dialog open={discard || blocker.state === 'blocked'} aria-labelledby={id + '-discard'}>
      <DialogTitle id={id + '-discard'}>Descartar alterações?</DialogTitle><DialogContent>Os dados deste formulário ainda não foram salvos.</DialogContent>
      <DialogActions><Button onClick={() => { setDiscard(false); if (blocker.state === 'blocked') blocker.reset(); }}>Continuar editando</Button>
        <Button color="error" onClick={() => { allowClose.current = true; setDiscard(false); if (blocker.state === 'blocked') blocker.proceed(); else onClose(); }}>Descartar</Button></DialogActions>
    </Dialog>
  </>;
}

