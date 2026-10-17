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
// 1.21.5 is where the protocol gained player_loaded and the NBT of entities moved to text components and `equipment`.
const [major, minor, patch = 0] = version.split('.').map(Number);
const since1215 = major > 1 || minor > 21 || (minor === 21 && patch >= 5);
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
    this.packets = [];         // name of every play packet received, in order
    this.windows = [];         // window ids of every open_window
    this.entities = new Map(); // entity uuid -> { id, x, y, z }, from spawn_entity
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
    this.client.on('login', () => this.loaded());
    this.client.on('custom_payload', (p) => {
      if (p.channel === 'minecraft:register') {
        // The library already splits this one into a list of names.
        const names = Array.isArray(p.data) ? p.data : p.data.toString('utf8').split('\0');
        this.serverRegister.push(...names.filter(Boolean));
      } else if (p.channel.startsWith('webgui:')) {
        this.received.push({ channel: p.channel, data: p.data, at: Date.now() });
      }
    });
    this.client.on('packet', (_data, meta) => { if (meta.state === 'play') this.packets.push(meta.name); });
    this.client.on('map_chunk', () => { this.chunksLoaded = true; });
    this.client.on('open_window', (p) => this.windows.push(p.windowId));
    this.client.on('spawn_entity', (p) => this.entities.set(String(p.objectUUID), { id: p.entityId, x: p.x, y: p.y, z: p.z }));
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

  /**
   * Since 1.21.5 the server holds a joining player invulnerable and deaf to interactions until the
   * client says its world has loaded; a real client does so after the first chunks. Older
   * protocols do not have the packet.
   */
  loaded() {
    if (since1215) this.client.write('player_loaded', {});
  }

  /** Waits until the world around the bot has arrived, so entities summoned next to it get tracked and it can be hurt. */
  async settle() {
    await this.waitFor(() => this.chunksLoaded, 8000, 'the first chunks');
    await sleep(500);
  }

  /** A click on an entity the way a vanilla client sends it: where it hit, then (unless it consumed it itself) the plain interact. */
  click(entityId, { at = true, plain = true, hand = 0, y = 0.5 } = {}) {
    if (at) this.client.write('use_entity', { target: entityId, mouse: 2, x: 0, y, z: 0, hand, sneaking: false });
    if (plain) this.client.write('use_entity', { target: entityId, mouse: 0, hand, sneaking: false });
  }

  /** Clicks "Respawn" and waits for the server to put the player back in the world. */
  async respawn() {
    const before = this.packets.filter((n) => n === 'respawn').length;
    this.client.write('client_command', { actionId: 'perform_respawn' });
    await this.waitFor(() => this.packets.filter((n) => n === 'respawn').length > before, 5000, 'the respawn');
    this.loaded(); // a respawn starts the "has the client loaded the world" wait again
    // Before 1.21.5 there is no such packet and a respawned player is simply untouchable for 60 ticks.
    await sleep(since1215 ? 500 : 3300);
  }

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

/** The four ints Minecraft's {UUID:[I;...]} wants for a uuid string. */
function uuidInts(uuid) {
  const hex = uuid.replaceAll('-', '');
  return [0, 8, 16, 24].map((i) => parseInt(hex.slice(i, i + 8), 16) | 0);
}

/** Summons a mob at the bot's feet with a known uuid and waits until the bot has seen it spawn. */
async function summon(b, uuid, type, nbt = '') {
  sendConsole(`execute at ${b.name} run summon ${type} ~ ~ ~ {UUID:[I;${uuidInts(uuid)}],NoAI:1b,Silent:1b,PersistenceRequired:1b${nbt ? ',' + nbt : ''}}`);
  await b.waitFor(() => b.entities.has(uuid), 5000, `${type} ${uuid} to spawn`);
  return b.entities.get(uuid);
}

/** Removes every non-player entity near the bots. Only entities in loaded chunks can be found, so call it once a bot is in the world. */
async function clearMobs() {
  sendConsole('kill @e[type=!player]');
  await sleep(1500); // a killed mob keeps its uuid until its death animation is over
}

async function bindEntity(uuid, url, cancel) {
  const since = readLog().length;
  sendConsole(`webgui bind entity ${uuid} "${url}" ${cancel}`);
  if (!await logHas(/bound 1 entity\/entities/, 4000, since)) throw new Error(`could not bind ${uuid}`);
}

async function unbindAndKill(...uuids) {
  for (const uuid of uuids) {
    sendConsole(`webgui unbind entity ${uuid}`);
    sendConsole(`kill ${uuid}`);
  }
  await sleep(300);
}

/** Console output for a command that prints to the log (data get ...), found by what it should contain. */
async function consoleSays(command, re, ms = 3000) {
  const since = readLog().length;
  sendConsole(command);
  return logHas(re, ms, since);
}

// --- scenarios ---

// A superflat world spawns slimes that kill idle bots mid-test. (The rule was renamed in 1.21.11; the console just rejects the one it does not know.)
sendConsole('gamerule doMobSpawning false');
sendConsole('gamerule spawn_mobs false');
sendConsole('kill @e[type=!player]');

// An aborted earlier run may have left a setting behind. The starter HUD is shown on join by
// default since 2.0.0; the checks below count the pages a join and each command send, so off.
setConfig({ requireClientMod: false, deathScreenUrl: '', autoHudOnJoin: false });
await reloadConfig();

const bots = [];
const track = (b) => { bots.push(b); return b; };

await scenario('A Fabric-like client: handshake, snapshot, assets', async () => {
  const joinLogAt = readLog().length;
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

  const ready = readLog().slice(joinLogAt).match(/client ready: e2e_fabric \(CURRENT\)/g) ?? [];
  check('WebGUIClientReadyEvent fires once for the WebGUI client', ready.length === 1, 'needs the debug log; see README');

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

// A player who died or was left in a bad spot keeps that state in the world's playerdata, and a bot that
// reuses the name then joins into it (we saw one that was sent a single chunk). Fresh names per run.
const RUN = Date.now().toString(36).slice(-4);

// Fixed uuids, so the bot can match spawn packets to what the console bound.
const U = (n) => `0e2e0000-0000-4000-8000-00000000000${n}`;
const [PIG, NAMED_PIG, PLAIN_PIG, VILLAGER, STAND_KEPT, STAND_EMPTIED, ZOMBIE] = [1, 2, 3, 4, 5, 6, 7].map(U);

await scenario('Entity bindings: one page per click, cancelInteraction', async () => {
  const b = track(new Bot(`e2e_click_${RUN}`));
  await b.joined();
  await b.settle();
  await clearMobs(); // left over from an earlier run: a summon with a uuid that exists is silently dropped

  const pig = await summon(b, PIG, 'minecraft:pig');
  const named = await summon(b, NAMED_PIG, 'minecraft:pig', !since1215 ? `CustomName:'{"text":"Bessie"}'` : `CustomName:'Bessie'`);
  const plain = await summon(b, PLAIN_PIG, 'minecraft:pig');
  await bindEntity(PIG, 'https://e2e.test/{entity_type}/{player_name}/{entity_uuid}', false);
  await bindEntity(NAMED_PIG, 'https://e2e.test/named', false);

  // A vanilla client's click on a pig: INTERACT_AT, then INTERACT because a pig does not consume the first.
  b.click(pig.id);
  await b.waitFor(() => b.got('open_web').length >= 1, 4000, 'the page for a click on a bound pig');
  await sleep(700); // long enough for a second page to show up if there were going to be one
  const opens = b.got('open_web');
  check('a click (INTERACT_AT + INTERACT) opens exactly one page', opens.length === 1, `${opens.length} open_web`);
  check('it is a gui with the placeholders resolved and the token appended',
    opens[0].mode === 0 && opens[0].url.startsWith(`https://e2e.test/minecraft:pig/${b.name}/${PIG}?webgui_token=`), opens[0].url.slice(0, 110));
  const contexts = b.got('entity_context');
  check('the entity context follows the open, once', contexts.length === 1
    && b.received.findIndex((m) => m.channel === 'webgui:open_web') < b.received.findIndex((m) => m.channel === 'webgui:entity_context'));
  const ctx = JSON.parse(contexts[0]?.json ?? 'null');
  check('context: uuid, registry type, English type name', ctx?.uuid === PIG && ctx.type === 'minecraft:pig' && ctx.name === 'Pig', JSON.stringify(ctx));
  check('context: position of the entity', ctx?.pos && Math.abs(ctx.pos.x - pig.x) < 1e-6 && Math.abs(ctx.pos.y - pig.y) < 1e-6 && Math.abs(ctx.pos.z - pig.z) < 1e-6,
    JSON.stringify(ctx?.pos));

  // an armor stand's click is INTERACT_AT only; a custom name wins over the type's
  let n = b.got('open_web').length;
  b.click(named.id, { plain: false });
  await b.waitFor(() => b.got('open_web').length > n, 4000, 'the page for INTERACT_AT alone');
  await sleep(500);
  check('INTERACT_AT alone opens one page too', b.got('open_web').length === n + 1);
  check('context: the custom name replaces the type name', JSON.parse(b.got('entity_context').at(-1).json).name === 'Bessie');

  // a client that skipped INTERACT_AT: not a click we count
  n = b.got('open_web').length;
  b.click(pig.id, { at: false });
  await sleep(800);
  check('a bare INTERACT (no INTERACT_AT) opens nothing', b.got('open_web').length === n);

  // the off hand never opens anything (a click is two hands' worth of packets)
  b.click(pig.id, { hand: 1 });
  await sleep(800);
  check('an off-hand click opens nothing', b.got('open_web').length === n);

  // an unbound entity
  const received = b.received.length;
  b.click(plain.id);
  await sleep(800);
  check('a click on an unbound entity sends nothing', b.received.length === received);

  // cancelInteraction on a villager: the trade window is the vanilla interaction (INTERACT, not INTERACT_AT)
  const villager = await summon(b, VILLAGER, 'minecraft:villager',
    `VillagerData:{profession:"minecraft:librarian",level:1,type:"minecraft:plains"},`
    + `Offers:{Recipes:[{buy:{id:"minecraft:emerald",count:1},sell:{id:"minecraft:bread",count:1},maxUses:99}]}`);
  await bindEntity(VILLAGER, 'https://e2e.test/shop/{entity_type}', false);
  n = b.got('open_web').length;
  b.click(villager.id);
  await b.waitFor(() => b.packets.includes('open_window'), 4000, 'the trade window when the interaction is kept');
  await b.waitFor(() => b.got('open_web').length > n, 4000, 'the page next to it');
  check('cancelInteraction=false: the page opens and the villager still trades', b.got('open_web').length === n + 1 && b.packets.includes('open_window'));
  b.client.write('close_window', { windowId: b.windows.at(-1) });
  await sleep(300);

  await bindEntity(VILLAGER, 'https://e2e.test/shop/{entity_type}', true);
  n = b.got('open_web').length;
  const windows = b.packets.filter((x) => x === 'open_window').length;
  b.click(villager.id);
  await b.waitFor(() => b.got('open_web').length > n, 4000, 'the page for a cancelled villager click');
  await sleep(700);
  check('cancelInteraction=true: one page and no trade window',
    b.got('open_web').length === n + 1 && b.packets.filter((x) => x === 'open_window').length === windows);

  // cancelInteraction on an armor stand: the equipment swap runs from INTERACT_AT
  const helmet = !since1215 ? 'ArmorItems:[{},{},{},{id:"minecraft:iron_helmet",count:1}]' : 'equipment:{head:{id:"minecraft:iron_helmet",count:1}}';
  const kept = await summon(b, STAND_KEPT, 'minecraft:armor_stand', helmet);
  const emptied = await summon(b, STAND_EMPTIED, 'minecraft:armor_stand', helmet);
  await bindEntity(STAND_KEPT, 'https://e2e.test/stand', true);
  await bindEntity(STAND_EMPTIED, 'https://e2e.test/stand', false);
  n = b.got('open_web').length;
  b.click(kept.id, { plain: false, y: 1.8 });
  b.click(emptied.id, { plain: false, y: 1.8 });
  await b.waitFor(() => b.got('open_web').length >= n + 2, 4000, 'a page for each armor stand');
  await sleep(700);
  check('an armor stand click opens one page each', b.got('open_web').length === n + 2);
  check('cancelInteraction=true keeps the helmet on the stand', await consoleSays(`data get entity ${STAND_KEPT}`, /iron_helmet/));
  check('cancelInteraction=false lets the vanilla swap take it (the control)', !await consoleSays(`data get entity ${STAND_EMPTIED}`, /iron_helmet/, 1500));

  await unbindAndKill(PIG, NAMED_PIG, PLAIN_PIG, VILLAGER, STAND_KEPT, STAND_EMPTIED);
  const store = JSON.parse(fs.readFileSync(path.join(dataDir, 'entity_bindings.json'), 'utf8'));
  check('no bindings left behind', store.bindings.length === 0, JSON.stringify(store.bindings));
  b.close();
});

await scenario('Death screen: info for the page', async () => {
  setConfig({ deathScreenUrl: '' });
  await reloadConfig();
  try {
    const info = (b) => b.got('death_screen').filter((m) => m.info).map((m) => ({ ...m, info: JSON.parse(m.info) }));

    // No deathScreenUrl: nothing to report, the vanilla screen handles it.
    const idle = track(new Bot(`e2e_idle_${RUN}`));
    await idle.joined();
    await idle.settle();
    await clearMobs();
    await idle.waitFor(() => idle.got('death_screen').length > 0, 6000, 'the snapshot');
    sendConsole(`kill ${idle.name}`);
    await sleep(1500);
    check('without deathScreenUrl a death sends no info', info(idle).length === 0 && idle.got('death_screen').every((m) => m.url === ''));
    await idle.respawn();
    idle.close();

    setConfig({ deathScreenUrl: 'https://e2e.test/death' });
    check('config reloaded', await reloadConfig());

    const victim = track(new Bot(`e2e_victim_${RUN}`));
    await victim.joined();
    await victim.settle();
    await victim.waitFor(() => victim.got('death_screen').length > 0, 6000, 'the snapshot');
    const joinSnapshot = victim.got('death_screen')[0];
    check('the join snapshot carries the url with a token and no info',
      joinSnapshot.url.startsWith('https://e2e.test/death?webgui_token=') && joinSnapshot.info === '', joinSnapshot.url.slice(0, 70));

    // /kill: the environment, the damage type "genericKill"
    sendConsole(`kill ${victim.name}`);
    await victim.waitFor(() => info(victim).length >= 1, 5000, 'the death info');
    await sleep(500);
    let deaths = info(victim);
    check('one info payload per death', deaths.length === 1, `${deaths.length}`);
    let d = deaths[0].info;
    console.log('INFO  /kill death info:', JSON.stringify(d));
    check('the payload repeats the url with a token', deaths[0].url.startsWith('https://e2e.test/death?webgui_token='));
    check('cause is the damage type msgId (genericKill)', d.cause === 'genericKill', d.cause);
    check('deathMessage is the English sentence', d.deathMessage === `${victim.name} was killed`, d.deathMessage);
    check('hardcore false, canRespawn true', d.hardcore === false && d.canRespawn === true);
    check('killer is the environment', d.killer.type === 'environment' && d.killer.name === null && d.killer.uuid === null, JSON.stringify(d.killer));
    await victim.respawn();

    // a mob: damage ... by <zombie>
    const zombie = await summon(victim, ZOMBIE, 'minecraft:zombie');
    sendConsole(`damage ${victim.name} 1000 minecraft:mob_attack by ${ZOMBIE}`);
    await victim.waitFor(() => info(victim).length >= 2, 5000, 'the death info for a mob kill');
    d = info(victim)[1].info;
    console.log('INFO  mob death info:', JSON.stringify(d));
    check('mob: cause "mob" and the English message names the zombie', d.cause === 'mob' && d.deathMessage === `${victim.name} was slain by Zombie`, `${d.cause} / ${d.deathMessage}`);
    check('mob: killer type, name, uuid and registry entity type',
      d.killer.type === 'mob' && d.killer.name === 'Zombie' && d.killer.uuid === ZOMBIE && d.killer.entityType === 'minecraft:zombie', JSON.stringify(d.killer));
    await victim.respawn();

    // another player
    const attacker = track(new Bot(`e2e_attack_${RUN}`));
    await attacker.joined();
    await attacker.settle();
    await attacker.waitFor(() => attacker.got('death_screen').length > 0, 6000, 'the snapshot');
    sendConsole(`damage ${victim.name} 1000 minecraft:player_attack by ${attacker.name}`);
    await victim.waitFor(() => info(victim).length >= 3, 5000, 'the death info for a player kill');
    d = info(victim)[2].info;
    console.log('INFO  player death info:', JSON.stringify(d));
    check('player: cause "player" and a "slain by" message', d.cause === 'player' && d.deathMessage === `${victim.name} was slain by ${attacker.name}`, `${d.cause} / ${d.deathMessage}`);
    check('player: killer type, name and uuid', d.killer.type === 'player' && d.killer.name === attacker.name && /^[0-9a-f-]{36}$/.test(d.killer.uuid) && d.killer.entityType === undefined,
      JSON.stringify(d.killer));
    check('nothing but the three deaths produced info', info(victim).length === 3);
    await victim.respawn(); // leave no dead player in the world's playerdata
    sendConsole(`kill ${ZOMBIE}`);
  } finally {
    sendConsole(`kill ${ZOMBIE}`);
    setConfig({ deathScreenUrl: '' });
    await reloadConfig();
  }
});

await scenario('An outdated client (open_web but no hello)', async () => {
  const since = readLog().length;
  const b = track(new Bot('e2e_old', { register: S2C.filter((c) => c !== 'webgui:hello') }));
  await b.joined();
  check('warned in the log', await logHas(/e2e_old has an outdated WebGUI/, 4000, since));
  check('no WebGUIClientReadyEvent for an outdated client', !/client ready: e2e_old/.test(readLog().slice(since)));
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
  check('no WebGUIClientReadyEvent for it either', !/client ready: e2e_vanilla/.test(readLog().slice(since)));
  b.close();
});

await scenario('requireClientMod', async () => {
  try {
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
  } finally {
    setConfig({ requireClientMod: false });
    await reloadConfig();
  }
});

for (const b of bots) b.close();

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
process.exit(failed.length ? 1 : 0);
