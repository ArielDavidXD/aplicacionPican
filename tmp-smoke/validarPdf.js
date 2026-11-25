// Valida estructura interna del PDF generado: cabecera, objetos, tabla xref y offsets.
const fs = require('fs');
const buf = fs.readFileSync('tmp-smoke/prueba.pdf');
const text = buf.toString('latin1');
let errores = [];

if (!text.startsWith('%PDF-1.4')) errores.push('cabecera %PDF-1.4 ausente');
if (!text.trimEnd().endsWith('%%EOF')) errores.push('marca %%EOF ausente');

// startxref
const idx = text.lastIndexOf('startxref');
if (idx < 0) { errores.push('startxref ausente'); }
else {
  const valor = parseInt(text.substring(idx + 9).trim(), 10);
  // leer xref
  const xrefIdx = text.lastIndexOf('xref');
  if (xrefIdx < 0) { errores.push('tabla xref ausente'); }
  else {
    const xrefText = text.substring(xrefIdx, idx);
    const lineas = xrefText.split('\n').map(l => l.trim()).filter(l => l.length > 0);
    if (lineas[1] !== '0 ' + (lineas.length - 2)) errores.push('conteo xref incoherente: ' + lineas[1]);
    for (let i = 2; i < lineas.length; i++) {
      const partes = lineas[i].split(' ');
      const offset = parseInt(partes[0], 10);
      const esperado = (i - 1) + ' 0 obj';
      const real = text.substring(offset, offset + esperado.length);
      if (real !== esperado) {
        errores.push('offset ' + i + ' incorrecto: esperado "' + esperado + '" en ' + offset + ', encontrado "' + real + '"');
      }
    }
  }
}

// comprobar emparejamiento BT/ET en cada stream
const streams = [];
let pos = 0;
while ((pos = text.indexOf('stream\n', pos)) !== -1) {
  const fin = text.indexOf('\nendstream', pos);
  streams.push(text.substring(pos + 7, fin));
  pos = fin + 1;
}
if (streams.length === 0) errores.push('no hay streams de contenido');
streams.forEach((s, i) => {
  const bt = (s.match(/BT/g) || []).length;
  const et = (s.match(/ET/g) || []).length;
  if (bt !== et) errores.push('stream ' + i + ': BT=' + bt + ' ET=' + et);
  if (s.includes('undefined')) errores.push('stream ' + i + ' contiene "undefined"');
});

// comprobar que el texto español está en WinAnsi
const contenido = streams.join('');
if (contenido.includes('?')) errores.push('posible sustitución de caracteres (?) en el contenido');

console.log('Objetos xref: ' + (lineas ? lineas.length - 2 : '?') + ' · Streams: ' + streams.length);
if (errores.length) {
  console.log('ERRORES:');
  errores.forEach(e => console.log('  - ' + e));
  process.exit(1);
} else {
  console.log('PDF estructuralmente válido.');
}
