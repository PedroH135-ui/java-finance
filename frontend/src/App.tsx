import { useState } from 'react';
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { AccountBalanceWalletOutlined, DashboardOutlined, SwapHoriz, AccountBalanceOutlined, CategoryOutlined, SettingsOutlined, PieChartOutlined, LinkOutlined, Menu, Logout, ChevronRight } from '@mui/icons-material';
import { Alert, AppBar, Avatar, Box, Button, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText, Stack, Toolbar, Typography } from '@mui/material';
import { api, ApiError } from './lib/api';
import type { Profile } from './lib/types';
import { AuthPage } from './features/auth/AuthPage';
import { Dashboard } from './features/dashboard/Dashboard';
import { Accounts } from './features/accounts/Accounts';
import { Categories } from './features/categories/Categories';
import { Movements } from './features/movements/Movements';
import { Settings } from './features/settings/Settings';
import { Budgets } from './features/budgets/Budgets';
import { Banking } from './features/banking/Banking';
import { Failure, Loading } from './components/Shared';
const links=[{path:'/',label:'Visão geral',icon:<DashboardOutlined/>},{path:'/movements',label:'Movimentações',icon:<SwapHoriz/>},{path:'/accounts',label:'Contas',icon:<AccountBalanceOutlined/>},{path:'/categories',label:'Categorias',icon:<CategoryOutlined/>},{path:'/budgets',label:'Orçamentos',icon:<PieChartOutlined/>},{path:'/banking',label:'Conexões bancárias',icon:<LinkOutlined/>},{path:'/settings',label:'Configurações',icon:<SettingsOutlined/>}];
export function App() {
  const me=useQuery({queryKey:['me'],queryFn:()=>api<Profile>('/auth/me'),staleTime:0});
  const client=useQueryClient(),location=useLocation(),navigate=useNavigate();
  const [drawer,setDrawer]=useState(false),[logoutError,setLogoutError]=useState<Error|null>(null);
  if(me.isPending)return <Loading/>;
  if(me.error){if(me.error instanceof ApiError&&me.error.status===401)return <AuthPage/>;return <Box p={4}><Failure error={me.error} retry={()=>void me.refetch()}/></Box>;}
  if(!me.data)return null;
  const profile=me.data,current=links.find(l=>l.path===location.pathname)??links[0];
  async function logout(){try{await api('/auth/logout',{method:'POST'});client.clear();navigate('/');window.location.reload();}catch(e){setLogoutError(e as Error);}}
  const nav=<Box sx={{height:'100%',display:'flex',flexDirection:'column',p:2}}>
    <Stack direction="row" alignItems="center" spacing={1.5} sx={{p:1.5,mb:3}}><Box sx={{bgcolor:'primary.main',color:'#fff',p:1,borderRadius:2,display:'flex'}}><AccountBalanceWalletOutlined/></Box><Typography variant="h6">Java Finance</Typography></Stack>
    <Typography variant="overline" color="text.secondary" sx={{px:2}}>Seu dia a dia</Typography>
    <List>{links.map(l=><Box key={l.path}>{l.path==='/settings'&&<Divider sx={{my:2}}/>}<ListItemButton component={RouterLink} to={l.path} selected={location.pathname===l.path} onClick={()=>setDrawer(false)} sx={{borderRadius:2,mb:0.5,'&.Mui-selected':{color:'primary.main',bgcolor:'#eaf0fc'}}}>
      <ListItemIcon sx={{minWidth:38,color:'inherit'}}>{l.icon}</ListItemIcon><ListItemText primary={l.label} slotProps={{primary:{fontSize:14,fontWeight:600}}}/></ListItemButton></Box>)}</List>
    <Box sx={{mt:'auto',p:2,bgcolor:'#f4f7fc',borderRadius:2}}><Typography variant="body2" fontWeight={650}>Seu controle começa aqui</Typography><Typography variant="caption" color="text.secondary">Cadastre suas contas e registre a primeira movimentação. Seus registros ficam separados por perfil.</Typography></Box>
  </Box>;
  return <Box sx={{display:'flex',minHeight:'100dvh'}}>
    <Box component="a" href="#main-content" sx={{position:'fixed',top:-100,left:16,zIndex:2000,'&:focus':{top:8,bgcolor:'white',p:2}}}>Pular para o conteúdo</Box>
    <Drawer variant="permanent" sx={{display:{xs:'none',lg:'block'},width:248,'& .MuiDrawer-paper':{width:248,boxSizing:'border-box'}}}>{nav}</Drawer>
    <Drawer open={drawer} onClose={()=>setDrawer(false)} sx={{'& .MuiDrawer-paper':{width:270}}}>{nav}</Drawer>
    <Box sx={{flex:1,minWidth:0}}>
      <AppBar position="sticky" color="inherit" elevation={0} sx={{borderBottom:1,borderColor:'divider'}}>
        <Toolbar sx={{gap:1}}>
          <IconButton aria-label="Abrir menu" onClick={()=>setDrawer(true)} sx={{display:{lg:'none'}}}><Menu/></IconButton>
          <Typography variant="body2" color="text.secondary" sx={{display:{xs:'none',sm:'block'}}}>Meu espaço</Typography><ChevronRight sx={{fontSize:16,color:'text.secondary',display:{xs:'none',sm:'block'}}}/><Typography variant="body2" fontWeight={600}>{current.label}</Typography>
          <Box sx={{flex:1}}/><Avatar sx={{width:32,height:32,bgcolor:'#e9effb',color:'primary.main',fontSize:14}}>{profile.name.slice(0,1).toUpperCase()}</Avatar>
          <Typography variant="body2" sx={{maxWidth:160,overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap',display:{xs:'none',sm:'block'}}}>{profile.name}</Typography>
          <Button size="small" startIcon={<Logout/>} onClick={()=>void logout()}>Sair</Button>
        </Toolbar>
      </AppBar>
      <Box component="main" id="main-content" tabIndex={-1} sx={{maxWidth:1440,mx:'auto',p:{xs:2,sm:3,md:4}}}>
        {import.meta.env.VITE_LOCAL_REVIEW==='true'&&<Alert severity="warning" sx={{mb:3}}>Ambiente local de demonstração com dados fictícios. Alterações independentes da produção. Conexão com o Nubank desativada.</Alert>}
        {logoutError&&<Failure error={logoutError}/>}
        {location.pathname==='/'?<Dashboard/>:location.pathname==='/accounts'?<Accounts/>:location.pathname==='/categories'?<Categories/>:location.pathname==='/movements'?<Movements/>:location.pathname==='/budgets'?<Budgets/>:location.pathname==='/banking'?<Banking/>:location.pathname==='/settings'?<Settings profile={profile}/>:<Box>Página não encontrada. <RouterLink to="/">Voltar à visão geral</RouterLink></Box>}
      </Box>
    </Box>
  </Box>;
}

