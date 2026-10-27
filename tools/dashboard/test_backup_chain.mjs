// 用法：先 `python3 - <<PY` 生成 wordbooks.html（import app.render_user_wordbooks），再 `node test_backup_chain.mjs wordbooks.html`；需 jsdom。
import { JSDOM } from 'jsdom';
import fs from 'fs';
const file = process.argv[2] || 'wordbooks.html';
const dom = new JSDOM(fs.readFileSync(file,'utf8'),{runScripts:'dangerously',url:'http://localhost/'});
const w = dom.window; await new Promise(r=>setTimeout(r,300));
const errs=[]; const chk=(n,c)=>{if(!c)errs.push(n)};
w.loadSample(); chk('schema', w.state.schema===1); chk('books', Array.isArray(w.state.wordbooks));
w.state.wordbooks[0].name='测试词书'; w.state.wordbooks[0].words.push('envelope-validation');
w.state.customEntries.push({word:'envelope-validation',translation:'信封校验'});
const out=JSON.parse(JSON.stringify(w.state)); chk('edit', out.wordbooks[0].words.includes('envelope-validation')); chk('custom', out.customEntries.some(e=>e.word==='envelope-validation'));
const backup={schema:1,activity:[],history:[{word:'most',ts:1}],notes:[],customEntries:[],wordbooks:[{name:'我的生词本',isDefault:true,words:['most'],learned:['most']}],external:[{word:'most'}],wordAudio:[{word:'most',uri:'file://a'}]};
w.state=backup; w.renderBooks(); w.renderCustoms();
for(const k of ['schema','activity','history','notes','customEntries','wordbooks','external','wordAudio']) chk('preserve '+k, k in w.state);
console.log(JSON.stringify({ok:errs.length===0,errs},null,2)); process.exit(errs.length?1:0);
