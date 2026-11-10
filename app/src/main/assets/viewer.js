'use strict';
const $=i=>document.getElementById(i),Q=new URLSearchParams(location.search);
const M=$('m'),N=$('n'),B=$('bar'),C=$('c'),VW=document.documentElement.clientWidth||innerWidth;
/* ---------- Language: the app passes ?l=en or ?l=ru (see R.string.web_lang) ---------- */
const LANG=Q.get('l')==='ru'?'ru':'en';
const T={
en:{
  libFail:"Couldn't load a component ({0})",
  opening:'Opening file…',converting:'Converting document…',
  failTitle:"Couldn't open this file",
  failHint:"Check that the file isn't damaged or protected by a password, or open it in another app from the ⋮ menu.",
  convStopped:'The converter stopped',convLoad:"The converter didn't load",convStart:"The converter didn't start",convFail:"The converter couldn't process this file",
  pages:'Pages',reading:'Reading view',
  notWord:"This isn't a Word document",pwDoc:'The document is protected by a password',badTable:"The document's table is damaged",noText:"The document's text wasn't found",
  oldDoc:'Legacy .doc format: only the text is shown. For the layout and pictures use ⋮ → “Open in another app”.',
  noContent:'content.xml is missing',noSheets:'The file has no sheets',first3000:'Showing the first 3,000 rows',
  slide:'Slide {0}',slides:'Slides',text:'Text',
  slidesFail:"Couldn't show the slide layout for this file, so the text is shown instead.",
  notPpt:"This isn't a PowerPoint presentation",
  oldPpt:'Legacy .ppt format: only the slide text is shown. For the layout use ⋮ → “Open in another app”.',
  noContainer:'META-INF/container.xml is missing',badFb2:'The FB2 file is damaged',first2m:'Showing the first 2 million characters',
  b:'B',kb:'KB',mb:'MB',emptyArchive:'The archive is empty',archiveContents:'Archive contents',
  unpackFail:"Couldn't unpack the archive (it may be protected by a password or damaged)",
  pwFile:'The file is protected by a password',unsupported:'This format is not supported',
  isPdf:'This is a PDF. Rename the file to .pdf',unsupportedYet:'This format is not supported yet',
  unavailable:"The file isn't available. Open it again with “Open file”."
},
ru:{
  libFail:'Не загрузился компонент ({0})',
  opening:'Открываю файл…',converting:'Конвертирую документ…',
  failTitle:'Не удалось открыть файл',
  failHint:'Проверьте, что файл не повреждён и не защищён паролем, или откройте его в другом приложении через меню ⋮.',
  convStopped:'Конвертер остановился',convLoad:'Не загрузился конвертер',convStart:'Конвертер не запустился',convFail:'Конвертер не смог обработать файл',
  pages:'Страницы',reading:'Для чтения',
  notWord:'Это не документ Word',pwDoc:'Документ защищён паролем',badTable:'Повреждена таблица документа',noText:'Не найден текст документа',
  oldDoc:'Старый формат .doc: показан только текст. Оформление и картинки доступны через меню ⋮ → «Открыть в другом приложении».',
  noContent:'Нет файла content.xml',noSheets:'В файле нет листов',first3000:'Показаны первые 3000 строк',
  slide:'Слайд {0}',slides:'Слайды',text:'Текст',
  slidesFail:'Оформление слайдов для этого файла показать не удалось, показан текст.',
  notPpt:'Это не презентация PowerPoint',
  oldPpt:'Старый формат .ppt: показан только текст слайдов. Оформление доступно через меню ⋮ → «Открыть в другом приложении».',
  noContainer:'Нет файла META-INF/container.xml',badFb2:'Файл FB2 повреждён',first2m:'Показаны первые 2 млн символов',
  b:'Б',kb:'КБ',mb:'МБ',emptyArchive:'Архив пуст',archiveContents:'Содержимое архива',
  unpackFail:'Не удалось распаковать архив (возможно, он защищён паролем или повреждён)',
  pwFile:'Файл защищён паролем',unsupported:'Формат не поддерживается',
  isPdf:'Это PDF, переименуйте файл в .pdf',unsupportedYet:'Этот формат пока не поддерживается',
  unavailable:'Файл недоступен: откройте его заново через «Открыть файл»'
}};
const i18n=(k,...a)=>(T[LANG][k]||T.en[k]||k).replace(/\{(\d+)\}/g,(m,i)=>a[i]);
document.documentElement.lang=LANG;
const SVG=d=>'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="'+d+'"/></svg>';
const ICON={warn:SVG('M10.3 4.2 2.6 17.5a2 2 0 0 0 1.7 3H19.7a2 2 0 0 0 1.7-3L13.7 4.2a2 2 0 0 0-3.4 0zM12 9.5v4M12 17h.01'),info:SVG('M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18zM12 11v5M12 8h.01')};
const esc=s=>String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const MIME={png:'image/png',jpg:'image/jpeg',jpeg:'image/jpeg',gif:'image/gif',webp:'image/webp',bmp:'image/bmp',svg:'image/svg+xml',mp3:'audio/mpeg',m4a:'audio/mp4',aac:'audio/aac',ogg:'audio/ogg',oga:'audio/ogg',opus:'audio/ogg',wav:'audio/wav',flac:'audio/flac',mp4:'video/mp4',m4v:'video/mp4',webm:'video/webm'};
const TEXT=new Set('txt log srt vtt ini cfg conf toml yml yaml xml css js mjs ts tsx jsx kt java py c cpp h hpp cs go rs php rb sh bat ps1 sql tex gradle properties html htm xhtml'.split(' '));
const extOf=n=>{const m=/\.([A-Za-z0-9]+)$/.exec(n);return m?m[1].toLowerCase():''};
const url=(b,t)=>URL.createObjectURL(new Blob([b],{type:t||''}));
const u8=b=>b instanceof Uint8Array?b:new Uint8Array(b);
const isPK=b=>{const u=u8(b);return u[0]===0x50&&u[1]===0x4B};
const libs={};
const L=s=>libs[s]||(libs[s]=new Promise((ok,no)=>{const e=document.createElement('script');e.src=s.includes('/')?s:'lib/'+s;e.onload=ok;e.onerror=()=>no(new Error(i18n('libFail',s)));document.head.appendChild(e)}));
let failed=false;
function busy(s){failed=false;M.hidden=false;M.className='';M.innerHTML='<div class="sp"></div><p></p>';M.lastChild.textContent=s}
function fail(e){failed=true;M.hidden=false;M.className='err';
  const m=String((e&&e.message)||e||'');
  M.innerHTML='<div class="eic">'+ICON.warn+'</div><h2></h2><p class="why"></p><p></p>';
  M.children[1].textContent=i18n('failTitle');M.children[2].textContent=m.charAt(0).toUpperCase()+m.slice(1);M.children[3].textContent=i18n('failHint')}
function note(s){N.hidden=false;N.innerHTML=ICON.info+'<span></span>';N.lastChild.textContent=s}
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
  async function go(i){bs.forEach((x,j)=>{x.className=j===i?'a':'';x.setAttribute('aria-pressed',j===i)});
    failed=false;M.hidden=true;N.hidden=true;
    try{await items[i][1]()}catch(e){fail(e)}
    if(!failed)M.hidden=true}
  await go(0);
}
/* ---------- ONLYOFFICE converter engine (x2t, WebAssembly) ---------- */
let x2tP=null,x2tN=0;
function x2tLoad(){return x2tP||(x2tP=new Promise((ok,no)=>{
  window.Module={onRuntimeInitialized:()=>{const X=window.Module;['/working','/working/media','/working/fonts','/working/themes'].forEach(d=>{try{X.FS.mkdir(d)}catch(e){}});ok(X)},onAbort:()=>no(new Error(i18n('convStopped')))};
  const e=document.createElement('script');e.src=new URL('lib/x2t.js',location.href).href;e.onerror=()=>no(new Error(i18n('convLoad')));document.head.appendChild(e)}))}
async function x2t(b,from,to){
  const X=await Promise.race([x2tLoad(),new Promise((_,no)=>setTimeout(()=>no(new Error(i18n('convStart'))),120000))]),n='in'+(++x2tN)+'.'+from,o='/working/'+n+'.'+to;
  X.FS.writeFile('/working/'+n,u8(b));
  X.FS.writeFile('/working/params.xml','<?xml version="1.0" encoding="utf-8"?><TaskQueueDataConvert xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><m_sFileFrom>/working/'+n+'</m_sFileFrom><m_sThemeDir>/working/themes</m_sThemeDir><m_sFileTo>'+o+'</m_sFileTo><m_bIsNoBase64>false</m_bIsNoBase64></TaskQueueDataConvert>');
  let out;try{X.ccall('main1','number',['string'],['/working/params.xml']);out=X.FS.readFile(o)}catch(e){throw new Error(i18n('convFail'))}
  try{X.FS.unlink('/working/'+n);X.FS.unlink(o)}catch(e){}
  return new Uint8Array(out).buffer}
async function viaX2t(b,from,to,render,fallback){
  busy(i18n('converting'));let out;
  try{out=await x2t(b,from,to)}catch(e){return fallback(b)}
  return render(out)}
/* ---------- Word ---------- */
async function rDocx(b){
  await L('jszip.min.js');await L('docx-preview.min.js');
  const pages=async()=>{C.style.zoom='';C.innerHTML='';await docx.renderAsync(b,C,null,{inWrapper:true,breakPages:true,useBase64URL:true});
    const s=C.querySelector('section.docx');if(s&&s.offsetWidth>0){const z=(VW-16)/s.offsetWidth;if(z<1)C.style.zoom=z}};
  const read=async()=>{C.style.zoom='';await L('mammoth.browser.min.js');const r=await mammoth.convertToHtml({arrayBuffer:b},{styleMap:["p[style-name='Title'] => h1:fresh","p[style-name='Subtitle'] => h2:fresh"]});C.innerHTML='<article class="r">'+r.value+'</article>'};
  await chips([[i18n('pages'),pages],[i18n('reading'),read]]);
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
  const wd=XLSX.CFB.find(c,'/WordDocument');if(!wd)throw new Error(i18n('notWord'));
  const d=new Uint8Array(wd.content),dv=new DataView(d.buffer),fl=dv.getUint16(10,true);
  if(fl&0x100)throw new Error(i18n('pwDoc'));
  const tb=XLSX.CFB.find(c,(fl&0x200)?'/1Table':'/0Table');if(!tb)throw new Error(i18n('badTable'));
  const t=new Uint8Array(tb.content),tv=new DataView(t.buffer);
  const ccp=dv.getInt32(0x4C,true),fc=dv.getUint32(0x1A2,true),lcb=dv.getUint32(0x1A6,true);
  let p=fc;while(p<fc+lcb&&t[p]===1)p+=3+tv.getUint16(p+1,true);
  if(t[p]!==2)throw new Error(i18n('noText'));
  const n=(tv.getUint32(p+1,true)-4)/12,pl=p+5,a=new TextDecoder('windows-1252'),w=new TextDecoder('utf-16le');let s='';
  for(let i=0;i<n;i++){const c0=tv.getUint32(pl+4*i,true),c1=tv.getUint32(pl+4*(i+1),true),f=tv.getUint32(pl+4*(n+1)+8*i+2,true),len=c1-c0;
    if(f&0x40000000){const o=(f&0x3FFFFFFF)>>1;s+=a.decode(d.subarray(o,o+len))}else s+=w.decode(d.subarray(f,f+2*len))}
  const ps=wclean(s.slice(0,ccp)).split('\n').filter(x=>x.trim());
  C.innerHTML='<article class="r">'+ps.map(x=>'<p>'+esc(x)+'</p>').join('')+'</article>';
  note(i18n('oldDoc'));
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
async function odfLoad(b){const z=await zip(b),f=z.file('content.xml');if(!f)throw new Error(i18n('noContent'));
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
/* ---------- Spreadsheets ---------- */
async function rSheet(d,type){
  await L('xlsx.full.min.js');
  const wb=XLSX.read(type==='string'?d:u8(d),{type:type||'array'});
  if(!wb.SheetNames.length)throw new Error(i18n('noSheets'));
  const show=n=>{const ws=wb.Sheets[n];let t=ws,extra='';const r=ws['!ref']&&XLSX.utils.decode_range(ws['!ref']);
    if(r&&r.e.r>3000){r.e.r=2999;t=Object.assign({},ws,{'!ref':XLSX.utils.encode_range(r)});extra='<p class="more">'+esc(i18n('first3000'))+'</p>'}
    C.innerHTML=XLSX.utils.sheet_to_html(t,{header:'',footer:''})+extra};
  if(wb.SheetNames.length===1)return show(wb.SheetNames[0]);
  await chips(wb.SheetNames.map(n=>[n,()=>show(n)]));
}
/* ---------- Presentations ---------- */
async function rPptx(b){
  await L('jszip.min.js');
  const text=async()=>{const z=await JSZip.loadAsync(b);
    const ns=Object.keys(z.files).filter(n=>/^ppt\/slides\/slide\d+\.xml$/.test(n)).sort((x,y)=>parseInt(x.match(/\d+/)[0])-parseInt(y.match(/\d+/)[0]));
    let h='';
    for(let i=0;i<ns.length;i++){const x=new DOMParser().parseFromString(await z.file(ns[i]).async('string'),'application/xml');
      const ps=[...x.getElementsByTagName('a:p')].map(p=>[...p.childNodes].map(n=>n.nodeName==='a:r'?((n.getElementsByTagName('a:t')[0]||{}).textContent||''):n.nodeName==='a:br'?'\n':'').join('')).filter(t=>t.trim());
      h+='<section class="sl"><h4>'+esc(i18n('slide',i+1))+'</h4>'+ps.map(t=>'<p>'+esc(t)+'</p>').join('')+'</section>'}
    C.innerHTML='<article class="r">'+h+'</article>'};
  const slides=async()=>{try{await L('pptx-preview.umd.js');
      // optional extras (more shapes, AlternateContent, fields, links); the library still works without them
      await L('pptx/shapes.js').catch(()=>0);await L('pptx/fix.js').catch(()=>0);
      C.innerHTML='';
      const w=Math.max(280,Math.min(VW-16,960)),nb=window.__ppPrep?await window.__ppPrep(b):b;
      await pptxPreview.init(C,{width:w}).preview(nb)}
    catch(e){await text();note(i18n('slidesFail'))}};
  await chips([[i18n('slides'),slides],[i18n('text'),text]]);
}
async function rPpt(b,cfb){
  await L('xlsx.full.min.js');
  const c=cfb||XLSX.CFB.read(u8(b),{type:'array'}),e=XLSX.CFB.find(c,'/PowerPoint Document');
  if(!e)throw new Error(i18n('notPpt'));
  const d=new Uint8Array(e.content),dv=new DataView(d.buffer),sl=[],a=new TextDecoder('windows-1252'),w=new TextDecoder('utf-16le');let cur=null;
  (function walk(s,end){while(s+8<=end){const vi=dv.getUint16(s,true),ty=dv.getUint16(s+2,true),len=dv.getUint32(s+4,true),bs=s+8,be=Math.min(bs+len,end);
    if((vi&0xF)===0xF){if(ty===1006){cur=[];sl.push(cur)}else if(ty===1008||ty===1016)cur=null;walk(bs,be)}
    else if(cur&&(ty===4000||ty===4008))cur.push((ty===4000?w:a).decode(d.subarray(bs,be)));
    s=be}})(0,d.length);
  C.innerHTML='<article class="r">'+sl.map((tx,i)=>'<section class="sl"><h4>'+esc(i18n('slide',i+1))+'</h4>'+tx.join('\n').split(/[\r\n\v]+/).filter(x=>x.trim()).map(x=>'<p>'+esc(x)+'</p>').join('')+'</section>').join('')+'</article>';
  note(i18n('oldPpt'));
}
async function rOdp(b){const[x,im]=await odfLoad(b);
  C.innerHTML='<article class="r">'+[...x.getElementsByTagName('draw:page')].map((p,i)=>'<section class="sl"><h4>'+esc(i18n('slide',i+1))+'</h4>'+odfHtml(p,im)+'</section>').join('')+'</article>'}
/* ---------- Books ---------- */
async function rEpub(b){
  const z=await zip(b),rd=async p=>{const f=z.file(p);return f?f.async('string'):null},dp=s=>new DOMParser().parseFromString(s,'application/xml');
  const cont=await rd('META-INF/container.xml');if(!cont)throw new Error(i18n('noContainer'));
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
  if(x.getElementsByTagName('parsererror').length)throw new Error(i18n('badFb2'));
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
/* ---------- Text and data ---------- */
function rText(s){const big=s.length>2e6;if(big)s=s.slice(0,2e6);const p=document.createElement('pre');p.className='t';p.textContent=s;C.appendChild(p);if(big)note(i18n('first2m'))}
function rJson(s){let o;try{o=JSON.parse(s)}catch(e){return rText(s)}
  const h=JSON.stringify(o,null,2).replace(/("(?:\\.|[^\\"])*")(\s*:)?|\b(?:true|false)\b|\bnull\b|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|[&<>]/g,(m,s,c)=>{
    if(m==='&')return'&amp;';if(m==='<')return'&lt;';if(m==='>')return'&gt;';
    if(s)return c?'<span class="k">'+esc(s)+'</span>'+c:'<span class="s">'+esc(s)+'</span>';
    return'<span class="'+(/^-?\d/.test(m)?'nu':m==='null'?'z':'b')+'">'+m+'</span>'});
  C.innerHTML='<pre class="t">'+h+'</pre>'}
async function rMd(s){await L('marked.min.js');await L('purify.min.js');C.innerHTML='<article class="r">'+DOMPurify.sanitize(marked.parse(s))+'</article>'}
function rBlob(ext,b){const m=MIME[ext],t=m.split('/')[0],u=url(b,m),tag=t==='video'?'video':'audio';
  C.innerHTML=t==='image'?'<img class="i" src="'+u+'">':'<'+tag+' controls src="'+u+'" style="width:100%"></'+tag+'>'}
/* ---------- Archives and format detection ---------- */
function fmtSize(n){return n==null?'':n<1024?n+' '+i18n('b'):n<1048576?Math.round(n/1024)+' '+i18n('kb'):(n/1048576).toFixed(1)+' '+i18n('mb')}
/* Colour of the extension tile in the archive list: the same families and tints as the file tiles of the app's recent list. */
const FAM=[['#c62828','pdf'],['#1565c0','docx doc dot dotx docm dotm odt rtf'],['#2e7d32','xlsx xlsm xlsb xls ods fods csv tsv'],['#e65100','pptx ppt pps ppsx pptm odp'],['#6a1b9a','jpg jpeg png webp bmp heic heif avif gif svg'],['#ad1457','mp4 m4v mkv webm 3gp mov mp3 m4a aac ogg oga opus flac wav'],['#00838f','html htm xhtml'],['#6d4c41','epub fb2'],['#f9a825','zip rar 7z tar gz tgz bz2 xz']];
function tile(e){
  const f=FAM.find(x=>x[1].split(' ').includes(e)),c=f?f[0]:(TEXT.has(e)||e==='md'||e==='json')?'#546e7a':'#607d8b',
    n=parseInt(c.slice(1),16),r=n>>16,g=(n>>8)&255,b=n&255,dk=document.documentElement.classList.contains('d'),m=v=>dk?Math.round(v+(255-v)*.45):v;
  return 'color:rgb('+m(r)+','+m(g)+','+m(b)+');background:rgba('+r+','+g+','+b+','+(dk?.22:.13)+')'}
function list(items,get){B.innerHTML='';failed=false;M.hidden=true;N.hidden=true;C.style.zoom='';
  C.innerHTML=items.length?items.map((x,i)=>{const e=extOf(x[0]);
    return '<div class="row" data-i="'+i+'"><div class="x" style="'+tile(e)+'">'+esc((e||'?').toUpperCase().slice(0,4))+'</div><div class="nm"><b>'+esc(x[0])+'</b><span>'+fmtSize(x[1])+'</span></div></div>'}).join(''):'<p class="more">'+esc(i18n('emptyArchive'))+'</p>';
  C.querySelectorAll('.row').forEach(r=>r.onclick=async()=>{const x=items[+r.dataset.i];await open(extOf(x[0]),await get(x[0]));
    const k=document.createElement('button');k.textContent='← '+i18n('archiveContents');k.onclick=()=>list(items,get);B.insertBefore(k,B.firstChild);scrollTo(0,0)})}
async function rArch(b,ext){
  await L('7zz.umd.js');
  const z=await window.SevenZip({wasmBinary:await(await fetch('lib/7zz.wasm')).arrayBuffer(),print:()=>{},printErr:()=>{}}),nm='a.'+(ext||'bin'),u=u8(b);
  const st=z.FS.open(nm,'w+');z.FS.write(st,u,0,u.length);z.FS.close(st);z.FS.mkdir('/out');
  try{z.callMain(['x',nm,'-o/out','-y'])}catch(e){}
  const it=[];(function walk(d){for(const n of z.FS.readdir(d)){if(n==='.'||n==='..')continue;const p=d+'/'+n,s=z.FS.stat(p);if(z.FS.isDir(s.mode))walk(p);else it.push([p.slice(5),s.size])}})('/out');
  if(!it.length)throw new Error(i18n('unpackFail'));
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
  if(has('/EncryptedPackage'))throw new Error(i18n('pwFile'));
  if(has('/WordDocument'))return viaX2t(b,'doc','docx',rDocx,x=>rDoc(x,c));
  if(has('/PowerPoint Document'))return viaX2t(b,'ppt','pptx',rPptx,x=>rPpt(x,c));
  if(has('/Workbook')||has('/Book'))return rSheet(b);
  throw new Error(i18n('unsupported'));
}
async function rAuto(b){
  const u=u8(b),sig=String.fromCharCode.apply(null,u.subarray(0,8));
  if(sig.startsWith('PK'))return rZip(b);
  if(u[0]===0xD0&&u[1]===0xCF&&u[2]===0x11&&u[3]===0xE0)return rCfb(b);
  if(sig.startsWith('{\\rtf'))return viaX2t(b,'rtf','docx',rDocx,rRtf);
  if(sig.startsWith('Rar!')||sig.startsWith('7z\xBC\xAF')||(u[0]===0x1F&&u[1]===0x8B)||sig.startsWith('BZh')||sig.startsWith('\xFD7zXZ'))return rArch(b,'');
  if(sig.startsWith('%PDF'))throw new Error(i18n('isPdf'));
  if(u.subarray(0,4000).includes(0))throw new Error(i18n('unsupportedYet'));
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
  busy(i18n('opening'));N.hidden=true;C.innerHTML='';B.innerHTML='';C.style.zoom='';
  try{await route(ext.toLowerCase(),b)}catch(e){fail(e)}
  if(!failed)M.hidden=true;
}
async function start(){
  if(Q.get('d')==='1')document.documentElement.classList.add('d');
  busy(i18n('opening'));
  document.addEventListener('click',e=>{const a=e.target.closest&&e.target.closest('a');if(!a)return;
    const h=a.getAttribute('href')||'';if(h[0]==='#'||/^(https?|mailto|tel):/i.test(h))return;e.preventDefault()});
  try{const r=await fetch('/doc/file');if(!r.ok)throw new Error(i18n('unavailable'));
    await open(Q.get('ext')||'',await r.arrayBuffer())}catch(e){fail(e)}
}
window.__open=open;
if(!window.__noauto)window.__ready=start();
