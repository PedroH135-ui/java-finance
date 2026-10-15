import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import { Alert, Box, Button, Chip, FormControlLabel, MenuItem, Paper, Stack, Switch, TextField, Typography } from '@mui/material';
import { Add, EditOutlined } from '@mui/icons-material';
import { api, json } from '../../lib/api';
import type { Category } from '../../lib/types';
import { Empty, Failure, FormDialog, Loading, PageHeader } from '../../components/Shared';
interface Values {name:string;kind:string;essential:boolean;archived:boolean}
export function Categories() {
  const query=useQuery({queryKey:['categories'],queryFn:()=>api<Category[]>('/categories')});
  const [editing,setEditing]=useState<Category|null|undefined>(undefined);
  return <>
    <PageHeader title="Categorias" subtitle="Entenda de onde vem e para onde vai seu dinheiro." action={<Button variant="contained" startIcon={<Add/>} onClick={()=>setEditing(null)}>Nova categoria</Button>}/>
    {query.isPending?<Loading/>:query.error?<Failure error={query.error}/>:query.data.length===0?<Paper variant="outlined"><Empty title="Dê nome aos seus gastos e receitas" action={<Button onClick={()=>setEditing(null)}>Criar categoria</Button>}>Comece com Salário, Alimentação ou Moradia. Você escolhe o que é essencial.</Empty></Paper>:
      <Stack spacing={1.5}>{query.data.map(c=><Paper key={c.id} variant="outlined" sx={{p:2}}>
        <Stack direction={{xs:'column',sm:'row'}} gap={1.5} alignItems={{sm:'center'}}>
          <Box sx={{flex:1,minWidth:0}}><Typography fontWeight={650} sx={{overflowWrap:'anywhere'}}>{c.name}</Typography><Typography color="text.secondary" variant="body2">{c.kind==='INCOME'?'Receita':'Despesa'}</Typography></Box>
          <Stack direction="row" gap={1} flexWrap="wrap">{c.essential&&<Chip label="Essencial" size="small" variant="outlined"/>}{c.archived&&<Chip label="Arquivada" size="small"/>}<Button startIcon={<EditOutlined/>} onClick={()=>setEditing(c)}>Editar</Button></Stack>
        </Stack>
      </Paper>)}</Stack>}
    {editing!==undefined&&<CategoryForm category={editing} onClose={()=>setEditing(undefined)}/>}
  </>;
}
function CategoryForm({category,onClose}:{category:Category|null;onClose:()=>void}) {
  const client=useQueryClient(),[error,setError]=useState('');
  const form=useForm<Values>({defaultValues:{name:category?.name??'',kind:category?.kind??'EXPENSE',essential:category?.essential??false,archived:category?.archived??false}});
  const submit=form.handleSubmit(async values=>{
    setError('');
    try{
      await api('/categories'+(category?'/'+category.id:''),{method:category?'PATCH':'POST',body:json(category?{name:values.name,essential:values.essential,archived:values.archived,version:category.version}:{name:values.name,kind:values.kind,essential:values.essential})});
      await client.invalidateQueries();onClose();
    }catch(e){setError((e as Error).message);}
  });
  return <FormDialog title={category?'Editar categoria':'Nova categoria'} dirty={form.formState.isDirty} busy={form.formState.isSubmitting} onClose={onClose}
    actions={<Button variant="contained" type="submit" form="category-form" disabled={form.formState.isSubmitting}>Salvar categoria</Button>}>
    <form id="category-form" onSubmit={submit}><Stack spacing={2.5} pt={1}>
      {error&&<Alert severity="error">{error}</Alert>}
      <TextField label="Nome" autoFocus required {...form.register('name',{required:'Informe um nome.',maxLength:80})} error={!!form.formState.errors.name} helperText={form.formState.errors.name?.message}/>
      {!category&&<Controller name="kind" control={form.control} render={({field})=><TextField select label="Natureza" {...field}><MenuItem value="EXPENSE">Despesa</MenuItem><MenuItem value="INCOME">Receita</MenuItem></TextField>}/>}
      {form.watch('kind')==='EXPENSE'&&<Controller name="essential" control={form.control} render={({field})=><FormControlLabel label="Despesa essencial" control={<Switch checked={field.value} onChange={field.onChange}/>}/>}/>}
      {category&&<Controller name="archived" control={form.control} render={({field})=><FormControlLabel label="Arquivar (preserva o histórico)" control={<Switch checked={field.value} onChange={field.onChange}/>}/>}/>}
    </Stack></form>
  </FormDialog>;
}

