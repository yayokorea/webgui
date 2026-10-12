// Headless protocol test for the Paper plugin.
//
// Boots nothing itself: start a server first (offline mode, plugin installed, debug logging
// for the "webgui" logger — see README.md), then run this against it. The bots speak the
// Minecraft protocol directly and pretend to be a WebGUI Fabric client, which on the wire is
// nothing more than a `minecraft:register` listing the webgui:* channels it can receive.
//
//   node e2e.mjs --run-dir ../run/1.21.11 --port 25611 --version 1.21.11 \
//                --console /tmp/paper-in-1.21.11
//
// --console is a FIFO (or any file the server reads as stdin) used to type server commands.

import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';

const require = createRequire(import.meta.url);
const mc = require('minecraft-protocol');

// --- arguments ---

const args = Object.fromEntries(process.argv.slice(2).reduce((acc, a, i, all) => {
  if (a.startsWith('--')) acc.push([a.slice(2), all[i + 1] && !all[i + 1].startsWith('--') ? all[i + 1] : 'true']);
  return acc;
}, []));
const host = args.host ?? '127.0.0.1';
const port = Number(args.port ?? 25565);
const version = args.version ?? '1.21.11';
const runDir = path.resolve(args['run-dir'] ?? '../run/1.21.11');
const consolePath = args.console;
if (!consolePath) {
  console.error('--console <path to the server stdin FIFO> is required');
  process.exit(2);
}
const dataDir = path.join(runDir, 'plugins', 'WebGUI');
const logPath = path.join(runDir, 'logs', 'latest.log');

// --- the protocol, as the plugin speaks it (WebviewProtocol / WebviewWire) ---

const S2C = ['open_web', 'set_main_menu', 'emit_to_page', 'entity_context', 'trusted_origins',
  'death_screen', 'hello', 'asset_manifest', 'asset_chunk'].map((p) => `webgui:${p}`);

function readVarInt(buf, pos) {
  let result = 0;
  for (let i = 0; i < 5; i++) {
    const b = buf[pos++];
    result |= (b & 127) << (i * 7);
    if ((b & 128) === 0) return [result, pos];
  }
  throw new Error('VarInt too big');
}
const writeVarInt = (n) => {
  const out = [];
  while (n & -128) { out.push((n & 127) | 128); n >>>= 7; }
  out.push(n);
  return Buffer.from(out);
};
function readString(buf, pos) {
  let len;
  [len, pos] = readVarInt(buf, pos);
  return [buf.toString('utf8', pos, pos + len), pos + len];
}
const writeString = (s) => { const b = Buffer.from(s, 'utf8'); return Buffer.concat([writeVarInt(b.length), b]); };

const decode = {
  hello(buf) { let [p, o] = readVarInt(buf, 0); let [v] = readString(buf, o); return { protocol: p, modVersion: v }; },
  trusted_origins(buf) { return { origins: readString(buf, 0)[0] }; },
  asset_manifest(buf) { let [r, o] = readString(buf, 0); let [m] = readString(buf, o); return { revision: r, manifest: m }; },
  death_screen(buf) { let [u, o] = readString(buf, 0); let [i] = readString(buf, o); return { url: u, info: i }; },
  set_main_menu(buf) { return { url: readString(buf, 0)[0] }; },
  entity_context(buf) { return { json: readString(buf, 0)[0] }; },
  open_web(buf) { let [p, o] = readVarInt(buf, 0); let [m, o2] = readVarInt(buf, o); let [u] = readString(buf, o2); return { protocol: p, mode: m, url: u }; },
  asset_chunk(buf) {
    let [p, o] = readString(buf, 0); let [i, o2] = readVarInt(buf, o); let [c, o3] = readVarInt(buf, o2);
    let [n, o4] = readVarInt(buf, o3);
    return { path: p, index: i, count: c, bytes: buf.subarray(o4, o4 + n) };
  },
};

// --- a tiny bot ---

class Bot {
  constructor(name, { register = S2C } = {}) {
    this.name = name;
    this.received = [];        // { channel, data } for every custom payload in the play phase
    this.serverRegister = [];  // channels the server announced in its own minecraft:register
    this.kicked = null;
    this.ended = false;
    this.client = mc.createClient({ host, port, username: name, version, auth: 'offline', hideErrors: true });
    this.client.on('login', () => {
      // A Fabric client does exactly this: as soon as it has the login packet, one register
      // payload with every channel it has a receiver for, separated by NUL bytes.
      if (register.length) {
        this.client.write('custom_payload', {
          channel: 'minecraft:register',
          data: Buffer.from(register.join('\0'), 'utf8'),
        });
      }
    });
    this.client.on('custom_payload', (p) => {
      if (p.channel === 'minecraft:register') {
        // The library already splits this one into a list of names.
        const names = Array.isArray(p.data) ? p.data : p.data.toString('utf8').split('\0');
        this.serverRegister.push(...names.filter(Boolean));
      } else if (p.channel.startsWith('webgui:')) {
        this.received.push({ channel: p.channel, data: p.data, at: Date.now() });
      }
    });
    const kick = (p) => { this.kicked = JSON.stringify(p); };
    this.client.on('kick_disconnect', kick);
    this.client.on('disconnect', kick);
    this.client.on('end', () => { this.ended = true; });
    this.client.on('error', (e) => { this.error = e; });
  }

  joined() { return new Promise((res, rej) => {
    this.client.once('login', res);
    this.client.once('end', () => rej(new Error(`${this.name} ended before joining`)));
    this.client.once('error', rej);
  }); }

  send(channel, data) { this.client.write('custom_payload', { channel, data }); }

  /** Messages on a channel, decoded. */
  got(channel) { return this.received.filter((m) => m.channel === `webgui:${channel}`).map((m) => ({ ...decode[channel]?.(m.data), raw: m.data })); }

  async waitFor(pred, ms, what) {
    const t0 = Date.now();
    while (Date.now() - t0 < ms) {
      if (pred()) return true;
      await sleep(50);
    }
    throw new Error(`timed out after ${ms} ms waiting for ${what}`);
  }

  close() { try { this.client.end(); } catch { /* already gone */ } }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const sendConsole = (line) => fs.appendFileSync(consolePath, line + '\n');
const readLog = () => (fs.existsSync(logPath) ? fs.readFileSync(logPath, 'utf8') : '');
async function logHas(re, ms, since = 0) {
  const t0 = Date.now();
  while (Date.now() - t0 < ms) {
    if (re.test(readLog().slice(since))) return true;
    await sleep(100);
  }
  return false;
}

// --- checks ---

const results = [];
function check(name, ok, detail = '') {
  results.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`);
}

async function scenario(title, fn) {
  console.log(`\n## ${title}`);
  try { await fn(); } catch (e) { check(`${title}: no exception`, false, e.stack ?? String(e)); }
}

function setConfig(patch) {
  const file = path.join(dataDir, 'server.json');
  const json = JSON.parse(fs.readFileSync(file, 'utf8'));
  Object.assign(json, patch);
  fs.writeFileSync(file, JSON.stringify(json, null, 2));
}
async function reloadConfig() {
  const since = readLog().length;
  sendConsole('webgui reload');
  return logHas(/config reloaded/, 5000, since);
}

// --- scenarios ---

const bots = [];
const track = (b) => { bots.push(b); return b; };

await scenario('A Fabric-like client: handshake, snapshot, assets', async () => {
  const b = track(new Bot('e2e_fabric'));
  await b.joined();
  await b.waitFor(() => b.got('hello').length > 0, 6000, 'webgui:hello');
  await b.waitFor(() => b.got('set_main_menu').length > 0, 3000, 'the config snapshot');

  check('server announces its incoming channels in minecraft:register',
    b.serverRegister.includes('webgui:page_event') && b.serverRegister.includes('webgui:asset_request'),
    JSON.stringify(b.serverRegister));

  const hello = b.got('hello')[0];
  check('hello carries protocol 1', hello.protocol === 1, JSON.stringify({ ...hello, raw: undefined }));
  check('hello carries the plugin version', /^\d+\.\d+\.\d+/.test(hello.modVersion), hello.modVersion);

  const order = b.received.map((m) => m.channel.slice(7));
  check('hello comes first, then the snapshot in mod order',
    JSON.stringify(order.slice(0, 5)) === JSON.stringify(['hello', 'trusted_origins', 'asset_manifest', 'death_screen', 'set_main_menu']),
    order.join(' '));

  const manifest = b.got('asset_manifest')[0];
  check('manifest lists the starter page', manifest?.manifest.includes('index.html'), manifest?.revision);

  // asset_request -> asset_chunk
  const index = fs.readFileSync(path.join(dataDir, 'web', 'index.html'));
  b.send('webgui:asset_request', writeString('index.html'));
  await b.waitFor(() => b.got('asset_chunk').length >= 1, 3000, 'an asset chunk');
  let chunks = b.got('asset_chunk');
  await b.waitFor(() => b.got('asset_chunk').length >= chunks[0].count, 3000, 'all chunks');
  chunks = b.got('asset_chunk');
  const body = Buffer.concat(chunks.map((c) => c.bytes));
  check('asset_request is answered with the file', chunks[0].path === 'index.html' && body.equals(index),
    `${chunks.length} chunk(s), ${body.length} bytes`);

  const before = b.got('asset_chunk').length;
  b.send('webgui:asset_request', writeString('nope.txt'));
  await b.waitFor(() => b.got('asset_chunk').length > before, 3000, 'the empty answer');
  const miss = b.got('asset_chunk').at(-1);
  check('a missing file still gets an answer (count 0)', miss.path === 'nope.txt' && miss.count === 0 && miss.bytes.length === 0);

  const before2 = b.got('asset_chunk').length;
  b.send('webgui:asset_request', writeString('../../server.json'));
  await b.waitFor(() => b.got('asset_chunk').length > before2, 3000, 'the traversal answer');
  const trav = b.got('asset_chunk').at(-1);
  check('path traversal is answered with nothing', trav.count === 0 && trav.bytes.length === 0, trav.path);

  // server -> client commands from the console
  sendConsole(`webgui gui ${b.name} https://example.com/gui`);
  await b.waitFor(() => b.got('open_web').length >= 1, 3000, 'open_web (gui)');
  const gui = b.got('open_web')[0];
  const ctxIdx = b.received.findIndex((m) => m.channel === 'webgui:entity_context');
  const openIdx = b.received.findIndex((m) => m.channel === 'webgui:open_web');
  check('gui: protocol 1, mode 0, token appended',
    gui.protocol === 1 && gui.mode === 0 && gui.url.startsWith('https://example.com/gui?webgui_token='), gui.url.slice(0, 60));
  check('gui: entity context is cleared first', ctxIdx >= 0 && ctxIdx < openIdx && b.got('entity_context')[0].json === 'null');

  sendConsole(`webgui hud ${b.name} https://example.com/hud`);
  await b.waitFor(() => b.got('open_web').length >= 2, 3000, 'open_web (hud)');
  check('hud: mode 1', b.got('open_web')[1].mode === 1);

  sendConsole('webgui gui @a https://example.com/all');
  await b.waitFor(() => b.got('open_web').length >= 3, 3000, 'open_web via @a');
  check('selector @a reaches the player', b.got('open_web')[2].url.startsWith('https://example.com/all'));

  // page events: valid ones fire the Bukkit event on the main thread
  let since = readLog().length;
  for (let i = 0; i < 3; i++) {
    b.send('webgui:page_event', Buffer.concat([writeString('quest'), writeString(`{"n":${i}}`)]));
  }
  check('page_event fires WebGUIPageEvent', await logHas(/page event quest from e2e_fabric/, 4000, since)
    && (readLog().slice(since).match(/page event quest from e2e_fabric/g) ?? []).length === 3,
    'needs the debug log; see README');

  // garbage never throws back or kicks
  b.send('webgui:page_event', Buffer.from([0xff, 0xff, 0xff, 0xff, 0xff, 0x01]));
  b.send('webgui:page_event', Buffer.alloc(0));
  b.send('webgui:asset_request', Buffer.from([0x05, 0x61]));
  await sleep(500);
  const beforeGarbage = b.got('asset_chunk').length;
  b.send('webgui:asset_request', writeString('index.html'));
  await b.waitFor(() => b.got('asset_chunk').length > beforeGarbage, 3000, 'an answer after garbage');
  check('malformed C2S payloads are dropped without kicking', !b.ended && b.kicked === null);
  check('server logs no exception for them', !/Exception/.test(readLog().slice(since)));

  // flood -> rate limit
  since = readLog().length;
  for (let i = 0; i < 120; i++) {
    b.send('webgui:page_event', Buffer.concat([writeString('flood'), writeString('{}')]));
  }
  check('a flood of page events is rate limited', await logHas(/dropping page events from e2e_fabric/, 4000, since));
  check('the flooding player is not kicked', !b.ended);

  // bind / unbind with a real entity
  // The zombie spawns next to the bot so it is in a loaded chunk; selectors only see loaded entities.
  sendConsole(`execute at ${b.name} run summon minecraft:zombie ~ ~ ~ {NoAI:1b,PersistenceRequired:1b}`);
  since = readLog().length;
  let bound = false;
  for (let attempt = 0; attempt < 8 && !bound; attempt++) {
    await sleep(750);
    sendConsole('webgui bind entity @e[type=zombie, limit=1] "https://x.test/a b" true');
    bound = await logHas(/bound 1 entity\/entities → https:\/\/x\.test\/a b/, 700, since);
  }
  check('bind: selector with spaces and quoted url', bound);
  const store = JSON.parse(fs.readFileSync(path.join(dataDir, 'entity_bindings.json'), 'utf8'));
  check('bind: persisted with cancel flag',
    store.bindings.length === 1 && store.bindings[0].urlTemplate === 'https://x.test/a b' && store.bindings[0].cancelInteraction === true);
  since = readLog().length;
  sendConsole('webgui unbind entity @e[type=zombie]');
  check('unbind: removes it', await logHas(/unbound 1 entity\/entities/, 4000, since));
  sendConsole('kill @e[type=zombie]');

  // reload pushes the snapshot again
  const snapshotsBefore = b.got('trusted_origins').length;
  await reloadConfig();
  await b.waitFor(() => b.got('trusted_origins').length > snapshotsBefore, 3000, 'the snapshot after /webgui reload');
  check('reload re-sends the config snapshot to online players', true);

  // Paper's cap on a serverbound custom payload is 32767 bytes
  const big = Buffer.concat([writeString('big'), writeString('x'.repeat(32768))]);
  b.send('webgui:page_event', big);
  await sleep(1500);
  console.log(`INFO  oversize page_event (${big.length} bytes): ${b.ended ? 'connection dropped' : 'accepted'}`
    + (b.kicked ? ` — ${b.kicked.slice(0, 160)}` : ''));
  check('oversize page_event outcome recorded', true, b.ended ? 'player disconnected' : 'accepted');
  b.close();
});

await scenario('An outdated client (open_web but no hello)', async () => {
  const since = readLog().length;
  const b = track(new Bot('e2e_old', { register: S2C.filter((c) => c !== 'webgui:hello') }));
  await b.joined();
  check('warned in the log', await logHas(/e2e_old has an outdated WebGUI/, 4000, since));
  await sleep(300);
  check('no hello is sent', b.got('hello').length === 0);
  check('not kicked by default', !b.ended);
  b.close();
});

await scenario('A vanilla client (never registers)', async () => {
  const since = readLog().length;
  const b = track(new Bot('e2e_vanilla', { register: [] }));
  await b.joined();
  await sleep(7000); // past the 5 s the plugin waits
  check('receives nothing from webgui', b.received.length === 0, b.received.map((m) => m.channel).join(' '));
  check('not kicked by default', !b.ended);
  check('no outdated warning for it', !/e2e_vanilla has an outdated/.test(readLog().slice(since)));
  b.close();
});

await scenario('requireClientMod', async () => {
  setConfig({ requireClientMod: true });
  check('config reloaded', await reloadConfig());

  const since = readLog().length;
  const vanilla = track(new Bot('e2e_refused', { register: [] }));
  await vanilla.joined();
  await vanilla.waitFor(() => vanilla.ended, 9000, 'the kick');
  check('a client without WebGUI is kicked with the mod-required text',
    /requires the WebGUI mod/.test(vanilla.kicked ?? ''), (vanilla.kicked ?? '').slice(0, 120));
  check('the refusal is logged', /refused e2e_refused/.test(readLog().slice(since)));

  const ok = track(new Bot('e2e_allowed'));
  await ok.joined();
  await ok.waitFor(() => ok.got('hello').length > 0, 6000, 'hello');
  await sleep(1000);
  check('a WebGUI client is let in and greeted', !ok.ended && ok.got('hello').length === 1);
  ok.close();

  setConfig({ requireClientMod: false });
  await reloadConfig();
});

for (const b of bots) b.close();

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
process.exit(failed.length ? 1 : 0);
