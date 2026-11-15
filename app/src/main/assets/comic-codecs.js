"use strict";
function __lrBytes(value) {
  if(value instanceof ArrayBuffer)return new Uint8Array(value);
  if(ArrayBuffer.isView(value))return new Uint8Array(value.buffer,value.byteOffset,value.byteLength);
  throw Error("转换接口需要 ArrayBuffer 或字节数组视图");
}
const Convert={
  md5(value) {
    return __lrComicCrypto.md5(__lrBytes(value)).buffer;
  },
  encryptAesEcb(value, key) {
    return __lrComicCrypto.ecb(__lrBytes(key), {disablePadding:true}).encrypt(__lrBytes(value)).buffer;
  },
  decryptAesEcb(value, key) {
    return __lrComicCrypto.ecb(__lrBytes(key), {disablePadding:true}).decrypt(__lrBytes(value)).buffer;
  },
  encodeUtf8(value) {
    const out=[];
    for(const char of String(value)) {
      let code=char.codePointAt(0);
      if(code>=0xd800&&code<=0xdfff)code=0xfffd;
      if(code<0x80)out.push(code);
      else if(code<0x800)out.push(0xc0|(code>>6),0x80|(code&63));
      else if(code<0x10000)out.push(0xe0|(code>>12),0x80|((code>>6)&63),0x80|(code&63));
      else out.push(0xf0|(code>>18),0x80|((code>>12)&63),0x80|((code>>6)&63),0x80|(code&63));
    }
    return Uint8Array.from(out).buffer;
  },
  decodeUtf8(value) {
    const bytes=__lrBytes(value);const out=[];
    for(let i=0;i<bytes.length;) {
      const a=bytes[i];let size=a<0x80?1:a>=0xc2&&a<=0xdf?2:a>=0xe0&&a<=0xef?3:a>=0xf0&&a<=0xf4?4:0;
      if(!size) { out.push('\ufffd');i++;continue; }
      let code=a&((1<<(8-size-1))-1);if(size===1)code=a;
      let consumed=1;let valid=true;
      for(let j=1;j<size;j++) {
        const b=bytes[i+j];
        if(b===undefined || b<0x80 || b>0xbf || j===1&&(a===0xe0&&b<0xa0||a===0xed&&b>0x9f||a===0xf0&&b<0x90||a===0xf4&&b>0x8f)) { valid=false;break; }
        consumed++;code=(code<<6)|(b&63);
      }
      out.push(valid?String.fromCodePoint(code):'\ufffd');i+=consumed;
    }
    return out.join('').replace(/^\ufeff/,'');
  },
  encodeBase64(value) {
    const b=__lrBytes(value),chars='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';let out='';
    for(let i=0;i<b.length;i+=3) {
      const n=(b[i]<<16)|((b[i+1]||0)<<8)|(b[i+2]||0);
      out+=chars[n>>>18]+chars[(n>>>12)&63]+(i+1<b.length?chars[(n>>>6)&63]:'=')+(i+2<b.length?chars[n&63]:'=');
    }
    return out;
  },
  decodeBase64(value) {
    let text=String(value).replace(/[\t\n\r ]/g,'').replace(/-/g,'+').replace(/_/g,'/');
    if(!/^[A-Za-z0-9+/]*={0,2}$/.test(text)||text.length%4===1||text.includes('=')&&text.length%4!==0)throw Error('Base64 格式无效');
    text=text.replace(/=+$/,'');const chars='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';const out=[];let bits=0,acc=0;
    for(const char of text) { acc=(acc<<6)|chars.indexOf(char);bits+=6;if(bits>=8){bits-=8;out.push((acc>>>bits)&255);} }
    return Uint8Array.from(out).buffer;
  },
  hexEncode(value) { return Array.from(__lrBytes(value),b=>b.toString(16).padStart(2,'0')).join(''); }
};
