import React from 'react';
import ReactDOM from 'react-dom/client';
import { CssBaseline, ThemeProvider } from '@mui/material';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider, createHashRouter } from 'react-router-dom';
import { theme } from './theme';
import { App } from './App';
import { ConnectionStatus } from './components/ConnectionStatus';
const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 15000, refetchOnWindowFocus: false }, mutations: { retry: false } } });
const router = createHashRouter([{ path: '*', element: <App/> }]);
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode><ThemeProvider theme={theme}><CssBaseline/><ConnectionStatus/><QueryClientProvider client={client}><RouterProvider router={router}/></QueryClientProvider></ThemeProvider></React.StrictMode>,
);
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => { void navigator.serviceWorker.register('/sw.js').catch(() => { /* A instalação pode ser tentada novamente na próxima visita. */ }); });
}

