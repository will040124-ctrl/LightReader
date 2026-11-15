"use strict";
// Venera-shaped DOM facade over a bundled parser. Parsing does not execute HTML scripts.
function __lrElement(node,owner) { return node&&node.nodeType===1?new HtmlElement(node,owner):null; }
class HtmlDocument {
  constructor(html) {
    if(typeof html!=='string'||Convert.encodeUtf8(html).byteLength>2*1024*1024)throw Error('HTML 文档过大或格式无效');
    this._document=__lrParseHTML(html).document;this._closed=false;
  }
  _check() { if(this._closed)throw Error('HTML 文档已释放'); }
  querySelector(selector) { this._check();return __lrElement(this._document.querySelector(selector),this); }
  querySelectorAll(selector) { this._check();return Array.from(this._document.querySelectorAll(selector),node=>__lrElement(node,this)); }
  getElementById(id) { this._check();return __lrElement(this._document.getElementById(String(id)),this); }
  dispose() { this._closed=true;this._document=null; }
}
class HtmlElement {
  constructor(node,owner) { this._node=node;this._owner=owner; }
  _check() { this._owner._check(); }
  get text() { this._check();return this._node.textContent||''; }
  get attributes() { this._check();return Object.fromEntries(Array.from(this._node.attributes,a=>[a.name,a.value])); }
  get children() { this._check();return Array.from(this._node.children,n=>__lrElement(n,this._owner)); }
  get nodes() { this._check();return Array.from(this._node.childNodes,n=>new HtmlNode(n,this._owner)); }
  get parent() { this._check();return __lrElement(this._node.parentElement,this._owner); }
  get innerHTML() { this._check();return this._node.innerHTML; }
  get innerHtml() { return this.innerHTML; }
  get classNames() { this._check();return Array.from(this._node.classList); }
  get id() { this._check();return this._node.getAttribute('id'); }
  get localName() { this._check();return this._node.localName; }
  get previousElementSibling() { this._check();return __lrElement(this._node.previousElementSibling,this._owner); }
  get nextElementSibling() { this._check();return __lrElement(this._node.nextElementSibling,this._owner); }
  querySelector(selector) { this._check();return __lrElement(this._node.querySelector(selector),this._owner); }
  querySelectorAll(selector) { this._check();return Array.from(this._node.querySelectorAll(selector),n=>__lrElement(n,this._owner)); }
  getElementById(id) { return this.querySelector('[id="'+String(id).replace(/\\/g,'\\\\').replace(/"/g,'\\"').replace(/[\r\n]/g,'')+'"]'); }
}
class HtmlNode {
  constructor(node,owner) { this._node=node;this._owner=owner; }
  get text() { this._owner._check();return this._node.textContent||''; }
  get type() { this._owner._check();return ({1:'element',3:'text',8:'comment',9:'document'})[this._node.nodeType]||'unknown'; }
  toElement() { this._owner._check();return __lrElement(this._node,this._owner); }
}
