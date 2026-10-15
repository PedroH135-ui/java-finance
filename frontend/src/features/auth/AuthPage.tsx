import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Box, Button, Chip, Divider, Paper, Stack, TextField, Typography } from '@mui/material';
import { AccountBalanceWalletOutlined, LockOutlined, CheckCircleOutline } from '@mui/icons-material';
import { api, ApiError, json } from '../../lib/api';
interface Credentials { email: string; password: string; name: string }
export function AuthPage() {
  const [registering,setRegistering]=useState(false), [error,setError]=useState(''), [notice,setNotice]=useState('');
  const client=useQueryClient();
  const config=useQuery({ queryKey:['auth-config'], queryFn:()=>api<{registrationEnabled:boolean;hosting:string}>('/auth/config') });
  const form=useForm<Credentials>({defaultValues:{email:'',password:'',name:''},shouldUnregister:true});
  async function submit(values:Credentials) {
    setError(''); setNotice('');
    try {
      if(registering) {
        await api('/auth/register',{method:'POST',body:json(values)});
        setRegistering(false); form.reset({...values,password:''}); setNotice('Conta criada. Entre com seu e-mail e senha.'); return;
      }
      const body=new URLSearchParams({username:values.email,password:values.password});
      await api('/auth/login',{method:'POST',body,headers:{'Content-Type':'application/x-www-form-urlencoded'}});
      await client.invalidateQueries({queryKey:['me']});
    } catch(e) { setError(e instanceof Error?e.message:'Não foi possível entrar.'); if(e instanceof ApiError) Object.entries(e.fields).forEach(([k,v])=>form.setError(k as keyof Credentials,{message:v})); }
  }
  return <Box sx={{ minHeight:'100dvh', display:'grid', gridTemplateColumns:{xs:'1fr',md:'1fr 1fr'}, bgcolor:'background.paper' }}>
    <Box sx={{ bgcolor:'#112d60', color:'#fff', p:{xs:3,md:7}, display:'flex', flexDirection:'column', justifyContent:'space-between', minHeight:{md:'100dvh'} }}>
      <Stack direction="row" spacing={1.5} alignItems="center"><AccountBalanceWalletOutlined/><Typography variant="h6">Java Finance</Typography><Chip label={config.data?.hosting==='cloud'?'WEB + APP':'LOCAL'} size="small" sx={{color:'#c3d7ff',bgcolor:'#ffffff15'}}/></Stack>
      <Box sx={{py:{xs:4,md:10},maxWidth:480}}>
        <Typography component="h1" sx={{fontSize:{xs:'2rem',md:'3.4rem'},lineHeight:1.12,fontWeight:750,letterSpacing:'-0.045em'}}>Mais clareza.<br/>Mais controle.<br/><Box component="span" sx={{color:'#a9c8ff'}}>Seu dinheiro.</Box></Typography>
        <Typography sx={{mt:3,color:'#d1ddf3',fontSize:'1.08rem'}}>Um espaço para acompanhar suas contas, organizar movimentações e entender o resultado de cada mês.</Typography>
        <Stack spacing={1.5} mt={4}>{['Dados separados por perfil','Sem conexão com bancos','Contas, receitas e despesas em um só lugar'].map(t=><Stack key={t} direction="row" spacing={1}><CheckCircleOutline sx={{fontSize:20,color:'#a9c8ff'}}/><Typography variant="body2">{t}</Typography></Stack>)}</Stack>
      </Box><Typography variant="caption" sx={{color:'#c3d2eb'}}>Finanças pessoais · BRL · America/Sao_Paulo</Typography>
    </Box>
    <Box sx={{p:{xs:3,md:6},display:'flex',alignItems:'center',justifyContent:'center'}}>
      <Paper sx={{width:'100%',maxWidth:410}}>
        <LockOutlined color="primary" sx={{mb:2}}/><Typography component="h2" variant="h4">{registering?'Crie seu espaço':'Bem-vindo de volta'}</Typography>
        <Typography color="text.secondary" mt={1} mb={4}>{registering?'Sua conta terá dados financeiros separados.':'Entre para acompanhar suas finanças.'}</Typography>
        <form onSubmit={form.handleSubmit(submit)}><Stack spacing={2.5}>
          {error&&<Alert severity="error">{error}</Alert>}{notice&&<Alert severity="success">{notice}</Alert>}
          {registering&&<TextField label="Seu nome" required autoComplete="name" {...form.register('name',{required:'Informe seu nome.'})} error={!!form.formState.errors.name} helperText={form.formState.errors.name?.message}/>}
          <TextField label="E-mail" type="email" required autoComplete="email" {...form.register('email',{required:'Informe o e-mail.'})} error={!!form.formState.errors.email} helperText={form.formState.errors.email?.message}/>
          <TextField label="Senha" type="password" required autoComplete={registering?'new-password':'current-password'} {...form.register('password',{required:'Informe a senha.',...(registering?{minLength:{value:10,message:'Use pelo menos 10 caracteres.'},maxLength:{value:64,message:'Use até 64 caracteres.'}}:{})})}
            error={!!form.formState.errors.password} helperText={form.formState.errors.password?.message ?? (registering?'Pelo menos 10 caracteres.':undefined)}/>
          <Button type="submit" variant="contained" size="large" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting?'Aguarde…':registering?'Criar conta':'Entrar'}</Button>
        </Stack></form>
        <Divider sx={{my:3}}/>
        {config.data?.registrationEnabled?<Button fullWidth onClick={()=>{setRegistering(!registering);setError('');form.clearErrors();}}>{registering?'Já tenho uma conta':'Criar minha conta'}</Button>:
          <Typography variant="body2" color="text.secondary">Novos cadastros estão desativados. Entre com uma conta já cadastrada.</Typography>}
        {config.isError&&<Alert severity="error" sx={{mt:2}}>Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.</Alert>}
      </Paper>
    </Box>
  </Box>;
}

