import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useQueryClient } from '@tanstack/react-query';
import { Alert,Box,Button,Stack,TextField,Typography } from '@mui/material';
import { api,json } from '../../lib/api';
import type { Profile } from '../../lib/types';
import { PageHeader,Panel } from '../../components/Shared';
import { MobileApp } from '../../components/MobileApp';
export function Settings({profile}:{profile:Profile}) {
  const client=useQueryClient(),[error,setError]=useState(''),[notice,setNotice]=useState('');
  const form=useForm({defaultValues:{name:profile.name}});
  const password=useForm({defaultValues:{currentPassword:'',newPassword:'',confirmation:''}});
  return <>
    <PageHeader title="Configurações" subtitle="Seu perfil e a segurança do seu espaço."/>
    {error&&<Alert severity="error" sx={{mb:2}}>{error}</Alert>}{notice&&<Alert severity="success" sx={{mb:2}}>{notice}</Alert>}
    <Box sx={{display:'grid',gridTemplateColumns:{xs:'1fr',md:'1fr 1fr'},gap:3}}>
      <Panel title="Perfil" subtitle={profile.email}><form onSubmit={form.handleSubmit(async values=>{setError('');try{await api('/auth/me',{method:'PATCH',body:json(values)});await client.invalidateQueries({queryKey:['me']});setNotice('Perfil atualizado.');form.reset(values);}catch(e){setError((e as Error).message);}})}>
        <Stack spacing={2}><TextField label="Seu nome" required {...form.register('name',{required:true,maxLength:100})}/><Button variant="contained" type="submit" disabled={form.formState.isSubmitting}>Salvar perfil</Button></Stack>
      </form></Panel>
      <Panel title="Alterar senha" subtitle="Todas as sessões anteriores serão encerradas."><form onSubmit={password.handleSubmit(async values=>{
        setError('');if(values.newPassword!==values.confirmation){password.setError('confirmation',{message:'As senhas não coincidem.'});return;}
        try{await api('/auth/password',{method:'POST',body:json({currentPassword:values.currentPassword,newPassword:values.newPassword})});client.clear();window.location.reload();}
        catch(e){setError((e as Error).message);}
      })}><Stack spacing={2}>
        <TextField label="Senha atual" type="password" autoComplete="current-password" required {...password.register('currentPassword',{required:true})}/>
        <TextField label="Nova senha" type="password" autoComplete="new-password" required {...password.register('newPassword',{required:true,minLength:{value:10,message:'Use pelo menos 10 caracteres.'},maxLength:64})} error={!!password.formState.errors.newPassword} helperText={password.formState.errors.newPassword?.message??'Pelo menos 10 caracteres.'}/>
        <TextField label="Confirme a nova senha" type="password" autoComplete="new-password" required {...password.register('confirmation',{required:true})} error={!!password.formState.errors.confirmation} helperText={password.formState.errors.confirmation?.message}/>
        <Button type="submit" variant="outlined" disabled={password.formState.isSubmitting}>Alterar senha e sair</Button>
      </Stack></form></Panel>
    </Box>
    <Box mt={3}><Panel title="Aplicativo no celular" subtitle="Android e iPhone"><MobileApp/></Panel></Box>
    <Typography variant="body2" color="text.secondary" mt={3}>Moeda: BRL · Fuso: America/Sao_Paulo · Dados separados por perfil · Conexão bancária opcional, somente leitura</Typography>
  </>;
}

