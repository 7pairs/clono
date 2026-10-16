// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {spawn} from 'node:child_process';
import {mkdir, writeFile} from 'node:fs/promises';
const env={...process.env, JAVA_HOME:'/Users/jun-haseba/.sdkman/candidates/java/21.0.12-tem'};
env.PATH=env.JAVA_HOME+'/bin:'+env.PATH;
await mkdir('results/logs', {recursive: true});
for (const [target,file] of [['probe-esm','results/build/esm/core.js'],['probe-cjs','results/build/probe.cjs']]) {
  const runtime=spawn(process.execPath,[file],{env});
  let runtimeLog='';
  runtime.stdout.on('data',d=>runtimeLog+=d); runtime.stderr.on('data',d=>runtimeLog+=d);
  await new Promise(r=>setTimeout(r,1500));
  const repl=spawn('./node_modules/.bin/shadow-cljs',['cljs-repl',target],{env});
  let output='';
  repl.stdout.on('data',d=>output+=d); repl.stderr.on('data',d=>output+=d);
  repl.stdin.end('(probe.core/echo #js {:title "REPL_OK" :depth 1})\n:cljs/quit\n');
  const timer=setTimeout(()=>repl.kill(),20000);
  const code=await new Promise(r=>repl.on('exit',r)); clearTimeout(timer);
  runtime.kill();
  await writeFile(`results/logs/repl-${target}.txt`,runtimeLog+'\n'+output);
  console.log(target,code,output);
}
