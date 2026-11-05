'use strict';
const $=i=>document.getElementById(i),Q=new URLSearchParams(location.search);
const M=$('m'),N=$('n'),B=$('bar'),C=$('c'),VW=document.documentElement.clientWidth||innerWidth;
const esc=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const MIME={png:'image/png',jpg:'image/jpeg',jpeg:'image/jpeg',gif:'image/gif',webp:'image/webp',bmp:'image/bmp',svg:'image/svg+xml',mp3:'audio/mpeg',m4a:'audio/mp4',aac:'audio/aac',ogg:'audio/ogg',oga:'audio/ogg',opus:'audio/ogg',wav:'audio/wav',flac:'audio/flac',mp4:'video/mp4',m4v:'video/mp4',webm:'video/webm'};
const TEXT=new Set('txt log srt vtt ini cfg conf toml yml yaml xml css js mjs ts tsx jsx kt java py c cpp h hpp cs go rs php rb sh bat ps1 sql tex gradle properties html htm xhtml'.split(' '));
const extOf=n=>{const m=/\.([A-Za-z0-9]+)$/.exec(n);return m?m[1].toLowerCase():''};
const url=(b,t)=>URL.createObjectURL(new Blob([b],{type:t||''}));
const u8=b=>b instanceof Uint8Array?b:new Uint8Array(b);
const isPK=b=>{const u=u8(b);return u[0]===0x50&&u[1]===0x4B};
const libs={};
const L=s=>libs[s]||(libs[s]=new Promise((ok,no)=>{const e=document.createElement('script');e.src='lib/'+s;e.onload=ok;e.onerror=()=>no(new Error('не загрузилась библиотека '+s));document.head.appendChild(e)}));
let failed=false;
function fail(e){failed=true;M.hidden=false;M.className='err';M.textContent='Не удалось открыть файл: '+((e&&e.message)||e)+'. Проверьте, что файл не повреждён и не защищён паролем, или откройте его в другом приложении через меню ⋮.'}
function note(t){N.hidden=false;N.textContent=t}
function decode(b){
  const u=u8(b);
  if(u[0]===0xEF&&u[1]===0xBB&&u[2]===0xBF)return new TextDecoder('utf-8').decode(u.subarray(3));
  if(u[0]===0xFF&&u[1]===0xFE)return new TextDecoder('utf-16le').decode(u.subarray(2));
  if(u[0]===0xFE&&u[1]===0xFF)return new TextDecoder('utf-16be').decode(u.subarray(2));
  try{return new TextDecoder('utf-8',{fatal:true}).decode(u)}catch(e){return new TextDecoder('windows-1251').decode(u)}
}
async function zip(b){await L('jszip.min.js');return JSZip.loadAsync(b,{decodeFileName:n=>{try{return new TextDecoder('utf-8',{fatal:true}).decode(n)}catch(e){return new TextDecoder('ibm866').decode(n)}}})}
async function chips(items){
  B.innerHTML='';
  const bs=items.map(([t],i)=>{const b=document.createElement('button');b.textContent=t;b.onclick=()=>go(i);B.appendChild(b);return b});
  async function go(i){bs.forEach((x,j)=>x.className=j===i?'a':'');try{await items[i][1]()}catch(e){fail(e)}}
  await go(0);
}
/* ---------- Движок конвертации ONLYOFFICE (x2t, WebAssembly) ---------- */
let x2tP=null,x2tN=0;
function x2tLoad(){return x2tP||(x2tP=new Promise((ok,no)=>{
  window.Module={onRuntimeInitialized:()=>{const X=window.Module;['/working','/working/media','/working/fonts','/working/themes'].forEach(d=>{try{X.FS.mkdir(d)}catch(e){}});ok(X)},onAbort:()=>no(new Error('конвертер остановился'))};
  const e=document.createElement('script');e.src=new URL('lib/x2t.js',location.href).href;e.onerror=()=>no(new Error('не загрузился конвертер'));document.head.appendChild(e)}))}
async function x2t(b,from,to){
  const X=await Promise.race([x2tLoad(),new Promise((_,no)=>setTimeout(()=>no(new Error('конвертер не запустился')),120000))]),n='in'+(++x2tN)+'.'+from,o='/working/'+n+'.'+to;
  X.FS.writeFile('/working/'+n,u8(b));
  X.FS.writeFile('/working/params.xml','<?xml version="1.0" encoding="utf-8"?><TaskQueueDataConvert xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><m_sFileFrom>/working/'+n+'</m_sFileFrom><m_sThemeDir>/working/themes</m_sThemeDir><m_sFileTo>'+o+'</m_sFileTo><m_bIsNoBase64>false</m_bIsNoBase64></TaskQueueDataConvert>');
  let out;try{X.ccall('main1','number',['string'],['/working/params.xml']);out=X.FS.readFile(o)}catch(e){throw new Error('конвертер не смог обработать файл')}
  try{X.FS.unlink('/working/'+n);X.FS.unlink(o)}catch(e){}
  return new Uint8Array(out).buffer}
async function viaX2t(b,from,to,render,fallback){
  M.textContent='Конвертирую документ…';let out;
  try{out=await x2t(b,from,to)}catch(e){return fallback(b)}
  return render(out)}
/* ---------- Word ---------- */
async function rDocx(b){
  await L('jszip.min.js');await L('docx-preview.min.js');
  const pages=async()=>{C.style.zoom='';C.innerHTML='';await docx.renderAsync(b,C,null,{inWrapper:true,breakPages:true,useBase64URL:true});
    const s=C.querySelector('section.docx');if(s&&s.offsetWidth>0){const z=(VW-16)/s.offsetWidth;if(z<1)C.style.zoom=z}};
  const read=async()=>{C.style.zoom='';await L('mammoth.browser.min.js');const r=await mammoth.convertToHtml({arrayBuffer:b},{styleMap:["p[style-name='Title'] => h1:fresh","p[style-name='Subtitle'] => h2:fresh"]});C.innerHTML='<article class="r">'+r.value+'</article>'};
  await chips([['Страницы',pages],['Для чтения',read]]);
}
function wclean(s){let o='';const st=[];
  for(const ch of s){const c=ch.charCodeAt(0);
    if(c===0x13){st.push(true);continue}
    if(c===0x14){if(st.length)st[st.length-1]=false;continue}
    if(c===0x15){st.pop();continue}
    if(st.includes(true))continue;
    o+=c===0x0D||c===0x0B?'\n':c===0x07?'\t':c===0x0C?'\n\n':(c<0x20&&c!==9&&c!==10)?'':ch}
  return o.replace(/\t+(?=\n)/g,'')}
async function rDoc(b,cfb){
  await L('xlsx.full.min.js');
  const c=cfb||XLSX.CFB.read(u8(b),{type:'array'});
  const wd=XLSX.CFB.find(c,'/WordDocument');if(!wd)throw new Error('это не документ Word');
  const d=new Uint8Array(wd.content),dv=new DataView(d.buffer),fl=dv.getUint16(10,true);
  if(fl&0x100)throw new Error('документ защищён паролем');
  const tb=XLSX.CFB.find(c,(fl&0x200)?'/1Table':'/0Table');if(!tb)throw new Error('повреждена таблица документа');
  const t=new Uint8Array(tb.content),tv=new DataView(t.buffer);
  const ccp=dv.getInt32(0x4C,true),fc=dv.getUint32(0x1A2,true),lcb=dv.getUint32(0x1A6,true);
  let p=fc;while(p<fc+lcb&&t[p]===1)p+=3+tv.getUint16(p+1,true);
  if(t[p]!==2)throw new Error('не найден текст документа');
  const n=(tv.getUint32(p+1,true)-4)/12,pl=p+5,a=new TextDecoder('windows-1252'),w=new TextDecoder('utf-16le');let s='';
  for(let i=0;i<n;i++){const c0=tv.getUint32(pl+4*i,true),c1=tv.getUint32(pl+4*(i+1),true),f=tv.getUint32(pl+4*(n+1)+8*i+2,true),len=c1-c0;
    if(f&0x40000000){const o=(f&0x3FFFFFFF)>>1;s+=a.decode(d.subarray(o,o+len))}else s+=w.decode(d.subarray(f,f+2*len))}
  const ps=wclean(s.slice(0,ccp)).split('\n').filter(x=>x.trim());
  C.innerHTML='<article class="r">'+ps.map(x=>'<p>'+esc(x)+'</p>').join('')+'</article>';
  note('Старый формат .doc: показан только текст. Оформление и картинки доступны через меню ⋮ → «Открыть в другом приложении».');
}
function rtf2txt(s,label){
  let o='',i=0,st=[],skip=false,uc=1,sk=0;const dec=new TextDecoder(label);
  const SK=/^(fonttbl|colortbl|stylesheet|info|pict|header|footer|footnote|themedata|colorschememapping|datastore|latentstyles|listtable|listoverridetable|rsidtbl|xmlnstbl|fldinst|generator)$/;
  while(i<s.length){const ch=s[i];
    if(ch==='{'){st.push([skip,uc]);i++;continue}
    if(ch==='}'){const x=st.pop()||[false,1];skip=x[0];uc=x[1];i++;continue}
    if(ch==='\\'){const nx=s[i+1];
      if(nx==="'"){const v=parseInt(s.substr(i+2,2),16);i+=4;if(sk>0){sk--;continue}if(!skip)o+=dec.decode(Uint8Array.of(v));continue}
      if(nx==='\\'||nx==='{'||nx==='}'){if(!skip)o+=nx;i+=2;continue}
      if(nx==='*'){skip=true;i+=2;continue}
      const m=/^\\([a-zA-Z]+)(-?\d+)? ?/.exec(s.slice(i,i+40));
      if(!m){i+=2;continue}
      i+=m[0].length;const w=m[1],v=m[2];
      if(SK.test(w)){skip=true;continue}
      if(w==='uc'){uc=+v;continue}
      if(w==='u'){if(!skip){let c=+v;if(c<0)c+=65536;o+=String.fromCharCode(c)}sk=uc;continue}
      if(!skip){if(w==='par'||w==='line'||w==='sect'||w==='page')o+='\n';else if(w==='tab')o+='\t'}
      continue}
    if(ch==='\r'||ch==='\n'){i++;continue}
    i++;if(sk>0){sk--;continue}if(!skip)o+=ch}
  return o}
function rRtf(b){
  let s='';const u=u8(b);for(let i=0;i<u.length;i+=8192)s+=String.fromCharCode.apply(null,u.subarray(i,i+8192));
  const cp=(/\\ansicpg(\d+)/.exec(s)||[])[1]||'1252';let label='windows-'+cp;try{new TextDecoder(label)}catch(e){label='windows-1252'}
  const ps=rtf2txt(s,label).split('\n').filter(x=>x.trim());
  C.innerHTML='<article class="r">'+ps.map(x=>'<p>'+esc(x)+'</p>').join('')+'</article>';
}
async function rOdt(b){const[x,im]=await odfLoad(b);C.innerHTML='<article class="r">'+odfHtml(x.getElementsByTagName('office:body')[0],im)+'</article>'}
async function odfLoad(b){const z=await zip(b),f=z.file('content.xml');if(!f)throw new Error('нет content.xml');
  const x=new DOMParser().parseFromString(await f.async('string'),'application/xml'),im={};
  for(const e of x.getElementsByTagName('draw:image')){const h=e.getAttribute('xlink:href');if(h&&!im[h]&&z.file(h))im[h]=url(await z.file(h).async('uint8array'),MIME[extOf(h)])}
  return[x,im]}
function odfHtml(n,im){let h='';
  for(const e of n.childNodes){
    if(e.nodeType===3){h+=esc(e.nodeValue);continue}
    if(e.nodeType!==1)continue;
    const t=e.nodeName,i=()=>odfHtml(e,im);
    if(t==='text:p')h+='<p>'+i()+'</p>';
    else if(t==='text:h'){const l=Math.min(+e.getAttribute('text:outline-level')||1,6);h+='<h'+l+'>'+i()+'</h'+l+'>'}
    else if(t==='text:s')h+=' '.repeat(+e.getAttribute('text:c')||1);
    else if(t==='text:tab')h+='\t';
    else if(t==='text:line-break')h+='<br>';
    else if(t==='text:list')h+='<ul>'+i()+'</ul>';
    else if(t==='text:list-item')h+='<li>'+i()+'</li>';
    else if(t==='table:table')h+='<table>'+i()+'</table>';
    else if(t==='table:table-row')h+='<tr>'+i()+'</tr>';
    else if(t==='table:table-cell')h+='<td>'+i()+'</td>';
    else if(t==='draw:image'){const u=im[e.getAttribute('xlink:href')];if(u)h+='<img src="'+u+'">'}
    else h+=i()}
  return h}
/* ---------- Таблицы ---------- */
async function rSheet(d,type){
  await L('xlsx.full.min.js');
  const wb=XLSX.read(type==='string'?d:u8(d),{type:type||'array'});
  if(!wb.SheetNames.length)throw new Error('в файле нет листов');
  const show=n=>{const ws=wb.Sheets[n];let t=ws,extra='';const r=ws['!ref']&&XLSX.utils.decode_range(ws['!ref']);
    if(r&&r.e.r>3000){r.e.r=2999;t=Object.assign({},ws,{'!ref':XLSX.utils.encode_range(r)});extra='<p class="r">Показаны первые 3000 строк</p>'}
    C.innerHTML=XLSX.utils.sheet_to_html(t,{header:'',footer:''})+extra};
  if(wb.SheetNames.length===1)return show(wb.SheetNames[0]);
  await chips(wb.SheetNames.map(n=>[n,()=>show(n)]));
}
/* ---------- Презентации ---------- */
async function rPptx(b){
  await L('jszip.min.js');
  const text=async()=>{const z=await JSZip.loadAsync(b);
    const ns=Object.keys(z.files).filter(n=>/^ppt\/slides\/slide\d+\.xml$/.test(n)).sort((x,y)=>parseInt(x.match(/\d+/)[0])-parseInt(y.match(/\d+/)[0]));
    let h='';
    for(let i=0;i<ns.length;i++){const x=new DOMParser().parseFromString(await z.file(ns[i]).async('string'),'application/xml');
      const ps=[...x.getElementsByTagName('a:p')].map(p=>[...p.childNodes].map(n=>n.nodeName==='a:r'?((n.getElementsByTagName('a:t')[0]||{}).textContent||''):n.nodeName==='a:br'?'\n':'').join('')).filter(t=>t.trim());
      h+='<section class="sl"><h4>Слайд '+(i+1)+'</h4>'+ps.map(t=>'<p>'+esc(t)+'</p>').join('')+'</section>'}
    C.innerHTML='<article class="r">'+h+'</article>'};
  const slides=async()=>{try{await L('pptx-preview.umd.js');C.innerHTML='';
      const w=Math.max(280,Math.min(VW-16,960));await pptxPreview.init(C,{width:w}).preview(b)}
    catch(e){await text();note('Оформление слайдов для этого файла показать не удалось, показан текст.')}};
  await chips([['Слайды',slides],['Текст',text]]);
}
async function rPpt(b,cfb){
  await L('xlsx.full.min.js');
  const c=cfb||XLSX.CFB.read(u8(b),{type:'array'}),e=XLSX.CFB.find(c,'/PowerPoint Document');
  if(!e)throw new Error('это не презентация PowerPoint');
  const d=new Uint8Array(e.content),dv=new DataView(d.buffer),sl=[],a=new TextDecoder('windows-1252'),w=new TextDecoder('utf-16le');let cur=null;
  (function walk(s,end){while(s+8<=end){const vi=dv.getUint16(s,true),ty=dv.getUint16(s+2,true),len=dv.getUint32(s+4,true),bs=s+8,be=Math.min(bs+len,end);
    if((vi&0xF)===0xF){if(ty===1006){cur=[];sl.push(cur)}else if(ty===1008||ty===1016)cur=null;walk(bs,be)}
    else if(cur&&(ty===4000||ty===4008))cur.push((ty===4000?w:a).decode(d.subarray(bs,be)));
    s=be}})(0,d.length);
  C.innerHTML='<article class="r">'+sl.map((t,i)=>'<section class="sl"><h4>Слайд '+(i+1)+'</h4>'+t.join('\n').split(/[\r\n\v]+/).filter(x=>x.trim()).map(x=>'<p>'+esc(x)+'</p>').join('')+'</section>').join('')+'</article>';
  note('Старый формат .ppt: показан только текст слайдов. Оформление доступно через меню ⋮ → «Открыть в другом приложении».');
}
async function rOdp(b){const[x,im]=await odfLoad(b);
  C.innerHTML='<article class="r">'+[...x.getElementsByTagName('draw:page')].map((p,i)=>'<section class="sl"><h4>Слайд '+(i+1)+'</h4>'+odfHtml(p,im)+'</section>').join('')+'</article>'}
/* ---------- Книги ---------- */
async function rEpub(b){
  const z=await zip(b),rd=async p=>{const f=z.file(p);return f?f.async('string'):null},dp=s=>new DOMParser().parseFromString(s,'application/xml');
  const cont=await rd('META-INF/container.xml');if(!cont)throw new Error('нет META-INF/container.xml');
  const opfPath=dp(cont).getElementsByTagName('rootfile')[0].getAttribute('full-path'),opf=dp(await rd(opfPath)),man={};
  for(const i of opf.getElementsByTagName('item'))man[i.getAttribute('id')]=i.getAttribute('href');
  const spine=[...opf.getElementsByTagName('itemref')].map(i=>man[i.getAttribute('idref')]).filter(Boolean);
  const base=opfPath.replace(/[^/]*$/,''),res=(dir,h)=>decodeURIComponent(new URL(h,'http://x/'+dir).pathname.slice(1));
  const art=document.createElement('article');art.className='r';C.appendChild(art);
  for(const h of spine){
    const p=res(base,h),s=await rd(p);if(!s)continue;
    const d=new DOMParser().parseFromString(s,'text/html'),dir=p.replace(/[^/]*$/,'');
    d.querySelectorAll('script,style,link,meta,title').forEach(e=>e.remove());
    for(const im of d.querySelectorAll('img,image')){
      const k=im.getAttribute('src')?'src':im.getAttribute('xlink:href')?'xlink:href':im.getAttribute('href')?'href':null;if(!k)continue;
      const v=im.getAttribute(k);if(/^(data|blob|https?):/.test(v))continue;
      const f=z.file(res(dir,v.split('#')[0]));if(f)im.setAttribute(k,url(await f.async('uint8array'),MIME[extOf(v)]))}
    const sec=document.createElement('section');sec.innerHTML=(d.body||d.documentElement).innerHTML;art.appendChild(sec);art.appendChild(document.createElement('hr'))}
}
function fb2(n,bin){let h='';
  for(const e of n.childNodes){
    if(e.nodeType===3){h+=esc(e.nodeValue);continue}
    if(e.nodeType!==1)continue;
    const t=e.localName,i=()=>fb2(e,bin);
    if(t==='p')h+='<p>'+i()+'</p>';
    else if(t==='title')h+='<h3>'+i().replace(/<\/?p>/g,' ')+'</h3>';
    else if(t==='subtitle')h+='<h4>'+i()+'</h4>';
    else if(t==='stanza')h+='<div class="st">'+i()+'</div>';
    else if(t==='v')h+='<div>'+i()+'</div>';
    else if(t==='text-author')h+='<p class="ta">'+i()+'</p>';
    else if(t==='empty-line')h+='<br>';
    else if(t==='emphasis')h+='<em>'+i()+'</em>';
    else if(t==='strong')h+='<b>'+i()+'</b>';
    else if(t==='strikethrough')h+='<s>'+i()+'</s>';
    else if(t==='sub'||t==='sup'||t==='code')h+='<'+t+'>'+i()+'</'+t+'>';
    else if(t==='image'){const a=[...e.attributes].find(x=>x.localName==='href'),u=a&&bin[a.value.replace('#','')];if(u)h+='<img src="'+u+'">'}
    else h+=i()}
  return h}
function rFb2(b){
  const u=u8(b),enc=(/encoding=["']([\w-]+)["']/i.exec(new TextDecoder('latin1').decode(u.subarray(0,200)))||[])[1]||'utf-8';
  let s;try{s=new TextDecoder(enc).decode(u)}catch(e){s=decode(u)}
  const x=new DOMParser().parseFromString(s,'application/xml');
  if(x.getElementsByTagName('parsererror').length)throw new Error('повреждённый FB2');
  const bin={};for(const e of x.getElementsByTagName('binary'))bin[e.getAttribute('id')]='data:'+(e.getAttribute('content-type')||'image/jpeg')+';base64,'+e.textContent.replace(/\s+/g,'');
  const ti=x.getElementsByTagName('title-info')[0];let h='';
  if(ti){const g=t=>(ti.getElementsByTagName(t)[0]||{}).textContent||'';
    const au=[...ti.getElementsByTagName('author')].map(a=>['first-name','middle-name','last-name'].map(t=>(a.getElementsByTagName(t)[0]||{}).textContent||'').filter(Boolean).join(' ')).join(', ');
    const cv=ti.getElementsByTagName('coverpage')[0];
    h+=(cv?fb2(cv,bin):'')+'<h1>'+esc(g('book-title'))+'</h1><p class="ta">'+esc(au)+'</p>';
    const an=ti.getElementsByTagName('annotation')[0];if(an)h+='<section>'+fb2(an,bin)+'</section><hr>'}
  for(const bd of x.getElementsByTagName('body'))h+='<section>'+fb2(bd,bin)+'</section>';
  C.innerHTML='<article class="r">'+h+'</article>';
}
/* ---------- Текст и данные ---------- */
function rText(s){const big=s.length>2e6;if(big)s=s.slice(0,2e6);const p=document.createElement('pre');p.className='t';p.textContent=s;C.appendChild(p);if(big)note('Показаны первые 2 млн символов')}
function rJson(s){let o;try{o=JSON.parse(s)}catch(e){return rText(s)}
  const h=JSON.stringify(o,null,2).replace(/("(?:\\.|[^\\"])*")(\s*:)?|\b(?:true|false)\b|\bnull\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|[&<>]/g,(m,s,c)=>{
    if(m==='&')return'&amp;';if(m==='<')return'&lt;';if(m==='>')return'&gt;';
    if(s)return c?'<span class="k">'+esc(s)+'</span>'+c:'<span class="s">'+esc(s)+'</span>';
    return'<span class="'+(/^-?\d/.test(m)?'nu':m==='null'?'z':'b')+'">'+m+'</span>'});
  C.innerHTML='<pre class="t">'+h+'</pre>'}
async function rMd(s){await L('marked.min.js');await L('purify.min.js');C.innerHTML='<article class="r">'+DOMPurify.sanitize(marked.parse(s))+'</article>'}
function rBlob(ext,b){const m=MIME[ext],t=m.split('/')[0],u=url(b,m),tag=t==='video'?'video':'audio';
  C.innerHTML=t==='image'?'<img class="i" src="'+u+'">':'<'+tag+' controls src="'+u+'" style="width:100%"></'+tag+'>'}
/* ---------- Архивы и определение формата ---------- */
function fmtSize(n){return n==null?'':n<1024?n+' Б':n<1048576?Math.round(n/1024)+' КБ':(n/1048576).toFixed(1)+' МБ'}
function list(items,get){B.innerHTML='';failed=false;M.hidden=true;N.hidden=true;C.style.zoom='';
  C.innerHTML=items.length?items.map((x,i)=>'<div class="row" data-i="'+i+'"><b>'+esc(x[0])+'</b><span>'+fmtSize(x[1])+'</span></div>').join(''):'<p class="r">Архив пуст</p>';
  C.querySelectorAll('.row').forEach(r=>r.onclick=async()=>{const x=items[+r.dataset.i];await open(extOf(x[0]),await get(x[0]));
    const k=document.createElement('button');k.textContent='← Содержимое архива';k.onclick=()=>list(items,get);B.insertBefore(k,B.firstChild)})}
async function rArch(b,ext){
  await L('7zz.umd.js');
  const z=await window.SevenZip({wasmBinary:await(await fetch('lib/7zz.wasm')).arrayBuffer(),print:()=>{},printErr:()=>{}}),nm='a.'+(ext||'bin'),u=u8(b);
  const st=z.FS.open(nm,'w+');z.FS.write(st,u,0,u.length);z.FS.close(st);z.FS.mkdir('/out');
  try{z.callMain(['x',nm,'-o/out','-y'])}catch(e){}
  const it=[];(function walk(d){for(const n of z.FS.readdir(d)){if(n==='.'||n==='..')continue;const p=d+'/'+n,s=z.FS.stat(p);if(z.FS.isDir(s.mode))walk(p);else it.push([p.slice(5),s.size])}})('/out');
  if(!it.length)throw new Error('не удалось распаковать архив (возможно, он защищён паролем или повреждён)');
  it.sort((x,y)=>x[0].localeCompare(y[0]));
  list(it,async n=>{const d=z.FS.readFile('/out/'+n);return d.buffer.slice(d.byteOffset,d.byteOffset+d.byteLength)});
}
async function rZip(b){
  const z=await zip(b),names=Object.keys(z.files).filter(n=>!z.files[n].dir).sort((x,y)=>x.localeCompare(y));
  const mt=z.file('mimetype')?(await z.file('mimetype').async('string')).trim():'';
  if(names.includes('word/document.xml'))return rDocx(b);
  if(names.includes('xl/workbook.xml')||names.includes('xl/workbook.bin'))return rSheet(b);
  if(names.includes('ppt/presentation.xml'))return rPptx(b);
  if(mt==='application/epub+zip')return rEpub(b);
  if(mt.endsWith('.text'))return viaX2t(b,'odt','docx',rDocx,rOdt);
  if(mt.endsWith('.presentation'))return viaX2t(b,'odp','pptx',rPptx,rOdp);
  if(mt.endsWith('.spreadsheet'))return rSheet(b);
  const fb=names.filter(n=>/\.fb2$/i.test(n));
  if(fb.length===1&&names.length===1)return rFb2(await z.file(fb[0]).async('arraybuffer'));
  list(names.map(n=>[n,z.file(n)._data&&z.file(n)._data.uncompressedSize]),n=>z.file(n).async('arraybuffer'));
}
async function rCfb(b){
  await L('xlsx.full.min.js');
  const c=XLSX.CFB.read(u8(b),{type:'array'}),has=p=>XLSX.CFB.find(c,p);
  if(has('/EncryptedPackage'))throw new Error('файл защищён паролем');
  if(has('/WordDocument'))return viaX2t(b,'doc','docx',rDocx,x=>rDoc(x,c));
  if(has('/PowerPoint Document'))return viaX2t(b,'ppt','pptx',rPptx,x=>rPpt(x,c));
  if(has('/Workbook')||has('/Book'))return rSheet(b);
  throw new Error('формат не поддерживается');
}
async function rAuto(b){
  const u=u8(b),sig=String.fromCharCode.apply(null,u.subarray(0,8));
  if(sig.startsWith('PK'))return rZip(b);
  if(u[0]===0xD0&&u[1]===0xCF&&u[2]===0x11&&u[3]===0xE0)return rCfb(b);
  if(sig.startsWith('{\\rtf'))return viaX2t(b,'rtf','docx',rDocx,rRtf);
  if(sig.startsWith('Rar!')||sig.startsWith('7z\xBC\xAF')||(u[0]===0x1F&&u[1]===0x8B)||sig.startsWith('BZh')||sig.startsWith('\xFD7zXZ'))return rArch(b,'');
  if(sig.startsWith('%PDF'))throw new Error('это PDF, переименуйте файл в .pdf');
  if(u.subarray(0,4000).includes(0))throw new Error('этот формат пока не поддерживается');
  return rText(decode(b));
}
async function route(ext,b){
  if(['docx','docm','dotx','dotm','pptx','pptm','ppsx','odt','odp','epub','xlsx'].includes(ext)&&!isPK(b))return rAuto(b);
  if(['doc','dot','ppt','pps'].includes(ext)&&isPK(b))return rZip(b);
  switch(ext){
    case 'docx':case 'docm':case 'dotx':case 'dotm':return rDocx(b);
    case 'doc':case 'dot':return viaX2t(b,ext,'docx',rDocx,rDoc);
    case 'xlsx':case 'xlsm':case 'xlsb':case 'xls':case 'ods':case 'fods':return rSheet(b);
    case 'csv':case 'tsv':return rSheet(decode(b),'string');
    case 'pptx':case 'pptm':case 'ppsx':return rPptx(b);
    case 'ppt':case 'pps':return viaX2t(b,ext,'pptx',rPptx,rPpt);
    case 'odt':return viaX2t(b,'odt','docx',rDocx,rOdt);case 'odp':return viaX2t(b,'odp','pptx',rPptx,rOdp);
    case 'rar':case '7z':case 'tar':case 'gz':case 'tgz':case 'bz2':case 'xz':return rArch(b,ext);
    case 'rtf':return viaX2t(b,'rtf','docx',rDocx,rRtf);case 'epub':return rEpub(b);case 'fb2':return rFb2(b);case 'zip':return rZip(b);
    case 'md':case 'markdown':return rMd(decode(b));case 'json':return rJson(decode(b));
  }
  if(MIME[ext])return rBlob(ext,b);
  if(TEXT.has(ext))return rText(decode(b));
  return rAuto(b);
}
async function open(ext,b){
  failed=false;M.hidden=false;M.className='';M.textContent='Открываю файл…';N.hidden=true;C.innerHTML='';B.innerHTML='';C.style.zoom='';
  try{await route(ext.toLowerCase(),b)}catch(e){fail(e)}
  if(!failed)M.hidden=true;
}
async function start(){
  if(Q.get('d')==='1')document.body.classList.add('d');
  document.addEventListener('click',e=>{const a=e.target.closest&&e.target.closest('a');if(!a)return;
    const h=a.getAttribute('href')||'';if(h[0]==='#'||/^(https?|mailto|tel):/i.test(h))return;e.preventDefault()});
  try{const r=await fetch('/doc/file');if(!r.ok)throw new Error('файл недоступен: откройте его заново через «Открыть файл»');
    await open(Q.get('ext')||'',await r.arrayBuffer())}catch(e){fail(e)}
}
window.__open=open;
if(!window.__noauto)window.__ready=start();
