import test from "node:test";
import assert from "node:assert/strict";
import vm from "node:vm";
import fs from "node:fs";
import { fileURLToPath } from "node:url";
const assets=new URL("../../app/src/main/assets/",import.meta.url);
const files=["comic-dom-vendor.js","comic-codecs.js","comic-host.js","comic-html.js"];
const host=files.map(file=>fs.readFileSync(new URL(file,assets),"utf8")).join("\n");
const mangaDexSource=fs.readFileSync(new URL('../../examples/comic-sources/mangadex-public.js',import.meta.url),'utf8');
function runtime(initial={data:{},settings:{}}) {
  const requests=[];
  const context=vm.createContext({__lrInitialState:structuredClone(initial),console:{log(line){requests.push(JSON.parse(line.slice("LR_COMIC_RPC:".length)));}}});
  vm.runInContext(host,context,{timeout:3000});
  return {context,requests,run:code=>vm.runInContext(code,context,{timeout:3000})};
}
test('MangaDex unavailable language reports readable alternatives rather than empty chapters',async()=>{
  const r=runtime({data:{},settings:{language:'en'}});
  r.run(mangaDexSource+`;const __lrSource=new MangaDexPublic();
    __lrSource.json=async path=>path.includes('/feed')?{total:path.includes('translatedLanguage')?0:1,data:path.includes('translatedLanguage')?[]:[{id:'c',attributes:{pages:20,translatedLanguage:'cs',isUnavailable:false}}]}:
    {data:{id:'6fef1f74-a0ad-4f0d-99db-d32a7cd24098',attributes:{title:{en:'Fire Punch'},altTitles:[],description:{}},relationships:[]}};`);
  await assert.rejects(r.run(`__lrSource.comic.loadInfo('6fef1f74-a0ad-4f0d-99db-d32a7cd24098')`),error=>error.message.includes('LR_CONTENT_UNAVAILABLE:')&&error.message.includes('捷克语'));
});
test('MangaDex all languages excludes unavailable and external chapters and separates translations',async()=>{
  const r=runtime({data:{},settings:{language:'all'}});
  r.run(mangaDexSource+`;const __lrSource=new MangaDexPublic();
    __lrSource.json=async path=>path.includes('/feed')?{total:4,data:[
      {id:'en',attributes:{pages:20,translatedLanguage:'en',isUnavailable:true}},
      {id:'cs',attributes:{pages:20,translatedLanguage:'cs',volume:'1',chapter:'1'}},
      {id:'sr',attributes:{pages:20,translatedLanguage:'sr',volume:'1',chapter:'1'}},
      {id:'external',attributes:{pages:20,translatedLanguage:'es',externalUrl:'https://example.com'}}]}:
    {data:{id:'6fef1f74-a0ad-4f0d-99db-d32a7cd24098',attributes:{title:{en:'Fire Punch'},altTitles:[],description:{}},relationships:[]}};`);
  const result=await r.run(`__lrSource.comic.loadInfo('6fef1f74-a0ad-4f0d-99db-d32a7cd24098').then(info=>JSON.stringify(__lrChapterEntries(info.chapters)))`);
  const chapters=JSON.parse(result);assert.deepEqual(chapters.map(c=>c.id),['cs','sr']);assert.match(chapters[0].volume,/捷克语/);assert.match(chapters[1].volume,/塞尔维亚语/);
});
test("HTML selectors, attributes, entities and source-style amp images",()=>{
  const r=runtime();
  assert.equal(r.run(`(()=>{const d=new HtmlDocument('<div class="comics-card"><a href="/comic/abc"><amp-img src="https://example.com/a?x=1&amp;y=2"></amp-img></a><h3>A &amp; B</h3></div>');const e=d.querySelector('div.comics-card');return e.querySelector('a > amp-img').attributes.src+'|'+e.querySelector('h3').text;})()`),"https://example.com/a?x=1&y=2|A & B");
});
test("nodes, parent, siblings, class names and both inner HTML spellings",()=>{
  const r=runtime();
  assert.equal(r.run(`(()=>{const d=new HtmlDocument('<section id="root"><b class="a b">One</b> text <!--note--><i>Two</i></section>');const root=d.getElementById('root'),b=root.children[0];return JSON.stringify([root.nodes.map(n=>n.type),b.parent.id,b.nextElementSibling.localName,root.children[1].previousElementSibling.localName,b.classNames,b.innerHTML===b.innerHtml,root.nodes[1].toElement()]);})()`),'[["element","text","comment","element"],"root","i","b",["a","b"],true,null]');
});
test("parsed HTML scripts never execute and disposed documents reject old elements",()=>{
  const r=runtime();
  assert.equal(r.run(`(()=>{const d=new HtmlDocument('<div id="x">ok</div><script>globalThis.injected=true</script>');const e=d.getElementById('x');d.dispose();let rejected=false;try{e.text}catch{rejected=true}return !globalThis.injected&&rejected;})()`),true);
});
test("UTF-8 handles Chinese, astral characters, surrogates and malformed bytes",()=>{
  const r=runtime();
  for(const value of ["轻阅猫娘 🌙","\ud800", "A\u0000B", ""]) {
    assert.equal(r.run(`Convert.hexEncode(Convert.encodeUtf8(${JSON.stringify(value)}))`),Buffer.from(value).toString("hex"));
  }
  for(const bytes of [[0xe0,0x80,0x80],[0xe1,0x80],[0xf4,0x90,0x80,0x80],[0xef,0xbb,0xbf,65],[255,128,65]])
    assert.equal(r.run(`Convert.decodeUtf8(new Uint8Array(${JSON.stringify(bytes)}))`),new TextDecoder().decode(new Uint8Array(bytes)));
});
test("Base64 and hex preserve arbitrary bytes and typed-array offsets",()=>{
  const r=runtime();
  const bytes=Array.from({length:256},(_,i)=>i);
  const encoded=Buffer.from(bytes).toString("base64");
  assert.equal(r.run(`Convert.encodeBase64(new Uint8Array(${JSON.stringify(bytes)}))`),encoded);
  assert.equal(r.run(`Convert.hexEncode(Convert.decodeBase64(${JSON.stringify(encoded)}))`),Buffer.from(bytes).toString("hex"));
  assert.equal(r.run("Convert.encodeBase64(new Uint8Array([9,1,2,3,9]).subarray(1,4))"),"AQID");
  assert.throws(()=>r.run("Convert.decodeBase64('A===')"));
});
test("source data copies JSON values, handles prototype names, settings and deletion",()=>{
  const r=runtime({data:{value:{n:1}},settings:{empty:""}});
  assert.equal(r.run(`(()=>{const s=new ComicSource();s.settings={language:{default:'cn'},empty:{default:'fallback'}};const copy=s.loadData('value');copy.n=99;s.saveData('__proto__',{safe:true});s.deleteData('value');return JSON.stringify([s.loadData('value'),s.loadData('__proto__'),s.loadSetting('language'),s.loadSetting('empty')]);})()`),'[null,{"safe":true},"cn",""]');
});
test("oversized storage leaves earlier values intact",()=>{
  const r=runtime();
  assert.equal(r.run(`(()=>{const s=new ComicSource();s.saveData('keep',1);try{s.saveData('large','猫'.repeat(50000))}catch{}return s.loadData('keep')===1&&s.loadData('large')===null;})()`),true);
});
test("binary network bridge and fetch response methods preserve bytes",async()=>{
  const r=runtime();
  const result=r.run(`(async()=>{const response=await fetch('https://example.com',{method:'POST',body:new Uint8Array([0,255,128])});return Convert.hexEncode(await response.arrayBuffer());})()`);
  assert.equal(r.requests[0].bodyEncoding,"base64");assert.equal(r.requests[0].data,"AP+A");assert.equal(r.requests[0].responseType,"bytes");
  r.run(`__lrResolve(1,{status:200,headers:{},body:'AP+A'});`);
  assert.equal(await result,"00ff80");
});
test("Map chapter groups preserve numeric key insertion order and prototype names",()=>{
  const r=runtime();
  assert.equal(r.run(`JSON.stringify(__lrChapterEntries(new Map([['卷二',new Map([['10','第十话'],['2','第二话']])],['__proto__',new Map([['constructor','番外']])]])))`),
    '[{"id":"10","title":"第十话","volume":"卷二"},{"id":"2","title":"第二话","volume":"卷二"},{"id":"constructor","title":"番外","volume":"__proto__"}]');
  assert.equal(r.run(`JSON.stringify(__lrChapterEntries({'正文':{'one':'第一话'}}))`),'[{"id":"one","title":"第一话","volume":"正文"}]');
  assert.throws(()=>r.run(`__lrChapterEntries(new Map([['bad',new Map([[1,'title']])]]))`));
});
test("source settings apply typed values, validate before changing and reset only settings",()=>{
  const r=runtime();
  r.run(`const __lrSource=new ComicSource();__lrSource.settings={quality:{type:'select',options:[{value:''},{value:'high'}],default:'high'},enabled:{type:'switch',default:true},name:{type:'input',validator:'^[a-z]+$',default:'abc'}};__lrSource.saveData('keep',1);`);
  r.run(`__lrApplySettings({quality:'',enabled:false,name:'reader'})`);
  assert.equal(r.run(`JSON.stringify([__lrSource.loadSetting('quality'),__lrSource.loadSetting('enabled'),__lrSource.loadSetting('name')])`),'["",false,"reader"]');
  assert.throws(()=>r.run(`__lrApplySettings({quality:'high',name:'123'})`));
  assert.equal(r.run(`__lrSource.loadSetting('quality')`),'');
  for(const edit of [`{quality:'bad'}`,`{enabled:'false'}`,`{unknown:1}`])assert.throws(()=>r.run(`__lrApplySettings(${edit})`));
  r.run(`__lrApplySettings({},true)`);
  assert.equal(r.run(`__lrSource.loadSetting('quality')`),'high');
  assert.equal(r.run(`__lrSource.loadData('keep')`),1);
});
test("search option defaults use values rather than display labels",()=>{
  const r=runtime();
  assert.equal(r.run(`JSON.stringify(__lrSearchOptions({optionList:[{options:['any-全部','popular-热门']},{type:'dropdown',options:['safe-安全']},{type:'multi-select',options:['a-A','b-B'],default:['b']}]}))`),'["any",null,"[\\"b\\"]"]');
});
