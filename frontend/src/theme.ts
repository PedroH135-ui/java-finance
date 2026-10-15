import { createTheme } from '@mui/material/styles';
export const theme = createTheme({
  palette: {
    mode: 'light', primary: { main: '#174bbd', dark: '#123984' },
    background: { default: '#f5f7fb', paper: '#ffffff' },
    text: { primary: '#18263e', secondary: '#526078' },
    success: { main: '#16734f' }, error: { main: '#b33440' }, warning: { main: '#8a5700' },
    divider: '#e1e7f0',
  },
  shape: { borderRadius: 10 },
  typography: {
    fontFamily: '"Segoe UI", system-ui, sans-serif',
    h4: { fontWeight: 750, fontSize: '1.85rem', letterSpacing: '-0.04em' },
    h5: { fontWeight: 700, fontSize: '1.3rem' }, h6: { fontWeight: 650, fontSize: '1.05rem' },
    button: { textTransform: 'none', fontWeight: 650 },
    body2: { lineHeight: 1.65 },
  },
  components: {
    MuiButton: { defaultProps: { disableElevation: true }, styleOverrides: { root: { minHeight: 44, borderRadius: 8 } } },
    MuiTextField: { defaultProps: { fullWidth: true, size: 'small', variant: 'outlined' } },
    MuiOutlinedInput: { styleOverrides: { root: { minHeight: 44, backgroundColor: '#fff' } } },
    MuiPaper: { defaultProps: { elevation: 0 }, styleOverrides: { outlined: { borderColor: '#e1e7f0' } } },
    MuiTableCell: { styleOverrides: { head: { color: '#526078', backgroundColor: '#f8faff', fontWeight: 650 }, root: { borderBottom: '1px solid #edf0f5' } } },
    MuiDialog: { defaultProps: { fullWidth: true, maxWidth: 'sm' } },
    MuiCssBaseline: { styleOverrides: {
      body: { margin: 0 }, '*': { boxSizing: 'border-box' },
      ':focus-visible': { outline: '3px solid #4478df', outlineOffset: 3 },
      '.money': { fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' },
      'a': { color: '#174bbd' },
    } },
  },
});

