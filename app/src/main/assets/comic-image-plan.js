"use strict";

// Describe the bitmap copies in the isolate. Pixels stay in Android's decoder.
function __lrImagePlan(code, width, height) {
  const original = Object.freeze({width, height});
  const copies = [];
  let output;
  const Image = Object.freeze({
    empty(w, h) {
      if (output || !Number.isSafeInteger(w) || !Number.isSafeInteger(h) || w < 1 || h < 1 || w * h > 12000000)
        throw Error('图片处理尺寸无效');
      output = Object.freeze({width:w, height:h,
        fillImageRangeAt(x, y, image, srcX, srcY, copyWidth, copyHeight) {
          const rect = [x, y, srcX, srcY, copyWidth, copyHeight];
          if (image !== original || copies.length >= 128 || !rect.every(Number.isSafeInteger))
            throw Error('图片处理范围无效');
          copies.push(rect);
        }
      });
      return output;
    }
  });
  const result = Function('image', 'Image', '"use strict";\n' + code + '\nreturn modifyImage(image);')(original, Image);
  if (result === original) return null;
  if (!output || result !== output || copies.length === 0) throw Error('图片处理未返回有效图像');
  return {width:output.width, height:output.height, copies};
}
