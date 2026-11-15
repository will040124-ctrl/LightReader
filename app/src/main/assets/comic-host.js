"use strict";
const APP=Object.freeze({locale:typeof __lrLocale==='string'?__lrLocale:'zh_CN'});
// Map is used for ordered, grouped chapter lists by existing sources.
function __lrJsonReplacer(key,value) {
  if(!(value instanceof Map))return value;
  const result=Object.create(null);
  for(const [name,item] of value) {
    if(typeof name!=='string'||Object.hasOwn(result,name))throw Error('目录 Map 键须为不重复的字符串');
    result[name]=item;
  }
  return result;
}
function __lrChapterEntries(chapters) {
  if(chapters==null)return null;
  const entries=value=>value instanceof Map?Array.from(value.entries()):
    value&&typeof value==='object'&&!Array.isArray(value)?Object.entries(value):(()=>{throw Error('此目录格式尚未支持')})();
  const result=[];
  function add(id,title,volume) {
    if(typeof id!=='string'||!id||typeof title!=='string'||!title.trim())throw Error('漫画话目录无效');
    if(result.length>=10000)throw Error('漫画话目录过大');
    result.push({id,title,volume});
  }
  for(const [key,value] of entries(chapters)) {
    if(value&&typeof value==='object')for(const [id,title] of entries(value))add(id,title,key);
    else add(key,value,'');
  }
  return result;
}
function __lrSearchOptions(search) {
  const fields=search.optionList||[];
  if(!Array.isArray(fields)||fields.length>16)throw Error('搜索选项格式尚未支持');
  return fields.map(field=>{
    if(!Array.isArray(field.options)||field.options.length>128)throw Error('搜索选项格式无效');
    const choices=field.options.map(option=>{
      if(typeof option!=='string')throw Error('搜索选项格式无效');
      return option.split('-')[0];
    });
    if(field.type==='multi-select') {
      const selected=field.default||[];
      if(!Array.isArray(selected)||selected.some(value=>!choices.includes(value)))throw Error('默认搜索选项无效');
      return JSON.stringify(selected);
    }
    const value=field.default??(field.type==='dropdown'?null:choices[0]??null);
    if(value!==null&&!choices.includes(value))throw Error('默认搜索选项无效');
    return value;
  });
}

function __lrBrowseOptions(fields, popular) {
  const options = __lrSearchOptions({optionList: fields});
  if (popular && fields?.[0]?.options?.some(option => option.split('-')[0] === 'mv')) options[0] = 'mv';
  return options;
}

function __lrBrowseCards(comics, limit) {
  if (!Array.isArray(comics)) throw Error('源未返回漫画列表');
  return comics.slice(0, limit).map(({id, title, subtitle, subTitle, cover, tags}) =>
    ({id, title, subtitle:subtitle ?? subTitle, cover, tags:Array.isArray(tags) ? tags.slice(0, 12) : []}));
}
function __lrApplySettings(values,reset=false) {
  for(const [key,value] of Object.entries(values)) {
    __lrCheckKey(key);
    if(!Object.hasOwn(__lrSource.settings||{},key))throw Error('源设置已变化，请重新打开');
    const field=__lrSource.settings[key];
    if(field.type==='select') {
      if(!Array.isArray(field.options)||!field.options.some(o=>o.value===value))throw Error('请选择有效的设置选项');
    } else if(field.type==='switch') {
      if(typeof value!=='boolean')throw Error('开关值无效');
    } else if(field.type==='input') {
      if(typeof value!=='string'||value.length>2048)throw Error('输入须在 2048 字符以内');
      if(field.validator!=null&&(typeof field.validator!=='string'||!new RegExp(field.validator).test(value)))throw Error('输入不符合源声明的格式');
    } else throw Error('此设置类型尚未支持');
  }
  if(reset)for(const key of Object.keys(__lrSettings))delete __lrSettings[key];
  for(const [key,value] of Object.entries(values))__lrSettings[key]=value;
  __lrExportState();return true;
}
// LightReader's host API subset; source state is injected only for the selected source.
const __lrData=Object.assign(Object.create(null),__lrInitialState.data||{});
const __lrSettings=Object.assign(Object.create(null),__lrInitialState.settings||{});
function __lrCheckKey(key) {
  if(typeof key!=='string'||key.length<1||key.length>128||/[\x00-\x1f\x7f-\x9f]/.test(key))throw Error('源存储键无效');
}
function __lrExportState() {
  const state={data:__lrData,settings:__lrSettings};
  if(Object.keys(__lrData).length>128||Object.keys(__lrSettings).length>128||Convert.encodeUtf8(JSON.stringify(state)).byteLength>128*1024)throw Error('本源存储超过限制');
  return state;
}
class ComicSource {
  loadData(key) { __lrCheckKey(key);return Object.hasOwn(__lrData,key)?JSON.parse(JSON.stringify(__lrData[key])):null; }
  saveData(key,value) {
    __lrCheckKey(key);const encoded=JSON.stringify(value);
    if(encoded===undefined)throw Error('源存储只支持 JSON 值');
    const present=Object.hasOwn(__lrData,key),old=__lrData[key];
    __lrData[key]=JSON.parse(encoded);
    try { __lrExportState(); } catch(error) { if(present)__lrData[key]=old;else delete __lrData[key];throw error; }
  }
  deleteData(key) { __lrCheckKey(key);delete __lrData[key]; }
  loadSetting(key) { __lrCheckKey(key);return Object.hasOwn(__lrSettings,key)?JSON.parse(JSON.stringify(__lrSettings[key])):this.settings?.[key]?.default??null; }
}
function Comic(data) { Object.assign(this, data); }
function ComicDetails(data) { Object.assign(this, data); }
function ImageLoadingConfig(data) { Object.assign(this, data); }
const __lrPending = new Map();
let __lrSequence = 0;
function __lrRequest(method, url, headers, data, responseType='text') {
  return new Promise((resolve, reject) => {
    const id = ++__lrSequence;
    __lrPending.set(id, {resolve, reject});
    const binary=data instanceof ArrayBuffer||ArrayBuffer.isView(data);
    console.log("LR_COMIC_RPC:" + JSON.stringify({id, method, url, headers: headers || {}, responseType,
      bodyEncoding:binary?'base64':'text',data:data==null?null:binary?Convert.encodeBase64(data):typeof data==='string'?data:JSON.stringify(data)}));
  });
}
function __lrResolve(id, packet) {
  const pending = __lrPending.get(id);
  if (!pending) return;
  __lrPending.delete(id);
  if (packet.error) pending.reject(new Error(packet.error)); else pending.resolve(packet);
}
const Network = {
  get: (url, headers) => __lrRequest("GET", url, headers),
  post: (url, headers, data) => __lrRequest("POST", url, headers, data),
  sendRequest: (method, url, headers, data) => __lrRequest(method, url, headers, data),
  fetchBytes: async (method,url,headers,data) => {
    const response=await __lrRequest(method,url,headers,data,'bytes');
    return {...response,body:Convert.decodeBase64(response.body)};
  }
};
async function fetch(url,options={}) {
  const response=await Network.fetchBytes(options.method||'GET',url,options.headers,options.body);
  return {ok:response.status>=200&&response.status<300,status:response.status,statusText:'',headers:response.headers,
    arrayBuffer:async()=>response.body,text:async()=>Convert.decodeUtf8(response.body),json:async()=>JSON.parse(Convert.decodeUtf8(response.body))};
}
