/**
 * 生成 copycat_guardrail 的 blockstate 与物品模型，并交叉校验 Java 侧的几何表。
 *
 * 几何本身不再产出模型文件：渲染由客户端代码直接从 GuardrailParts 发射几何体，
 * 再把伪装材质模型逐面裁剪进去（与 Create 的伪装板、Create: Copycats+ 做法一致）。
 * 因此 blockstate 的全部变体都指向 minecraft:block/air —— Create 自己的伪装方块也是这样。
 *
 * 本脚本保留 JS 侧的几何推导，用来交叉校验 Java 手写的几何表与角柱归属规则，
 * 并做角柱重复自检。改动几何时两边的期望值都由这里给出。
 *
 * 用法： node tools/gen-copycat-guardrail.mjs
 */

import { writeFileSync, mkdirSync, readFileSync, existsSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';

const NS = 'maris-decoration';
const ASSETS = join(process.cwd(), 'common/src/main/resources/assets', NS);
const BLOCK_DIR = join(ASSETS, 'models/block/copycat_guardrail');
const BLOCKSTATE = join(ASSETS, 'blockstates/copycat_guardrail.json');
const ITEM_MODEL = join(ASSETS, 'models/item/copycat_guardrail.json');
const STALE_MARKER_DIR = join(ASSETS, 'textures/block/copycat_guardrail/marker');

/** 未伪装时的默认外观，与 Create 的 copycat 保持一致。 */
const DEFAULT_TEXTURE = 'create:block/copycat_base';

/**
 * 全部 8 个材质槽位，键名与 Java 侧 GuardrailParts.materialKey 一致：
 * 4 根横梁按<b>方向</b>（{@code north_row}…），4 根柱子按<b>角点</b>（{@code 0_0}…）。
 * 柱子不按方向归属——一个角点会被两个方向共用，两个方向各存一份就会存出两份材质。
 */
const SLOT_KEYS = [
    ...['north', 'east', 'south', 'west'].map((dir) => `${dir}_row`),
    ...['15_0', '15_15', '0_15', '0_0'],
];

/** 角落代号 → 角点键名（与 Java 侧 columnKey 一致：角点在 1/16 单位下的 (x, z)）。 */
const CORNER_KEY = { NE: '15_0', SE: '15_15', SW: '0_15', NW: '0_0' };

/** 基准单面模型 = guardrail/straight.json 的东面（facing=east，不旋转）。 */
const BASE = {
    // [x0, y0, z0, x1, y1, z1]
    postShared: [15, 0, 0, 16, 16, 1],     // 东面模型的北端柱 -> 东北角
    postOwned: [15, 0, 15, 16, 16, 16],    // 东面模型的南端柱 -> 东南角
    rails: [
        [15, 5, 1, 16, 6, 15],
        [15, 10, 1, 16, 11, 15],
        [15, 15, 1, 16, 16, 15],
    ],
};

/** 各方向的旋转角与「共享柱」所在角落的归属方向。 */
const FACINGS = {
    east: { rot: 0, sharedOwner: 'north' },    // 共享角 = 东北，归 north
    south: { rot: 90, sharedOwner: 'east' },   // 共享角 = 东南，归 east
    west: { rot: 180, sharedOwner: 'south' },  // 共享角 = 西南，归 south
    north: { rot: 270, sharedOwner: 'west' },  // 共享角 = 西北，归 west
};

/** 各方向的自有柱 / 共享柱分别落在哪个角落。 */
const CORNER_OF = {
    east: { owned: 'SE', shared: 'NE' },
    south: { owned: 'SW', shared: 'SE' },
    west: { owned: 'NW', shared: 'SW' },
    north: { owned: 'NE', shared: 'NW' },
};

const ORDER = ['north', 'east', 'south', 'west'];
const LETTER = { north: 'n', east: 'e', south: 's', west: 'w' };

/** 把方块模型绕 Y 轴旋转 rot 度（与 blockstate 的 "y" 语义一致）。 */
function rotateBox(box, rot) {
    const [x0, y0, z0, x1, y1, z1] = box;
    switch (rot) {
        case 0: return [x0, y0, z0, x1, y1, z1];
        case 90: return [16 - z1, y0, x0, 16 - z0, y1, x1];
        case 180: return [16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0];
        case 270: return [z0, y0, 16 - x1, z1, y1, 16 - x0];
        default: throw new Error(`bad rotation ${rot}`);
    }
}

/** 计算一个长方体六个面的 UV，遵循 Minecraft 的投影约定。 */
function faceUv(box, face) {
    const [x0, y0, z0, x1, y1, z1] = box;
    switch (face) {
        case 'down':
        case 'up': return [x0, z0, x1, z1];
        case 'north': return [16 - x1, 16 - y1, 16 - x0, 16 - y0];
        case 'south': return [x0, 16 - y1, x1, 16 - y0];
        case 'west': return [z0, 16 - y1, z1, 16 - y0];
        case 'east': return [16 - z1, 16 - y1, 16 - z0, 16 - y0];
        default: throw new Error(`bad face ${face}`);
    }
}

/** 贴在方块边界上的面才加 cullface，这样相邻实心方块能正确剔除。 */
function cullFace(box, face) {
    const [x0, y0, z0, x1, y1, z1] = box;
    switch (face) {
        case 'down': return y0 === 0 ? 'down' : null;
        case 'up': return y1 === 16 ? 'up' : null;
        case 'north': return z0 === 0 ? 'north' : null;
        case 'south': return z1 === 16 ? 'south' : null;
        case 'west': return x0 === 0 ? 'west' : null;
        case 'east': return x1 === 16 ? 'east' : null;
        default: return null;
    }
}

function toElement(box, texture) {
    const faces = {};
    for (const face of ['down', 'up', 'north', 'south', 'west', 'east']) {
        const entry = { uv: faceUv(box, face), texture };
        const cull = cullFace(box, face);
        if (cull) entry.cullface = cull;
        faces[face] = entry;
    }
    return {
        from: [box[0], box[1], box[2]],
        to: [box[3], box[4], box[5]],
        faces,
    };
}

/** 某个方向在本组合下的几何贡献。柱子按角点而不是方向命名槽位。 */
function contributions(dir, present) {
    const { rot, sharedOwner } = FACINGS[dir];
    const corners = CORNER_OF[dir];
    const out = [];
    for (const rail of BASE.rails) out.push({ box: rotateBox(rail, rot), key: `${dir}_row` });
    out.push({ box: rotateBox(BASE.postOwned, rot), key: CORNER_KEY[corners.owned] });
    // 共享柱：只有归属方向缺席时才补画，避免与对方重合。
    if (!present.has(sharedOwner)) {
        out.push({ box: rotateBox(BASE.postShared, rot), key: CORNER_KEY[corners.shared] });
    }
    return out;
}

function buildParts(present) {
    const parts = [];
    for (const dir of ORDER) {
        if (present.has(dir)) parts.push(...contributions(dir, present));
    }
    return parts;
}

/** 物品模型：没有方块实体，必须走静态模型。只放一个面（东面 = 基准朝向）。 */
function itemModelJson(present) {
    return JSON.stringify(
        {
            parent: `${NS}:block/thin_side_block`,
            textures: { particle: '#all', all: DEFAULT_TEXTURE },
            elements: buildParts(present).map((p) => toElement(p.box, '#all')),
        },
        null,
        4,
    ) + '\n';
}

// ---- 生成 ----

mkdirSync(BLOCK_DIR, { recursive: true });

const variants = {};
const combos = [];
for (let mask = 0; mask < 16; mask++) {
    const present = new Set(ORDER.filter((_, i) => mask & (1 << (3 - i))));
    const name = ORDER.filter((d) => present.has(d)).map((d) => LETTER[d]).join('') || 'none';
    combos.push({ present, name });

    // 几何体由客户端代码发射，blockstate 只需要一个能烘焙的占位模型
    variants[ORDER.map((d) => `${d}=${present.has(d)}`).join(',')] = { model: 'minecraft:block/air' };
}

writeFileSync(BLOCKSTATE, JSON.stringify({ variants }, null, 4) + '\n');

mkdirSync(dirname(ITEM_MODEL), { recursive: true });
writeFileSync(join(BLOCK_DIR, 'item.json'), itemModelJson(new Set(['east'])));
writeFileSync(ITEM_MODEL, JSON.stringify({ parent: `${NS}:block/copycat_guardrail/item` }, null, 4) + '\n');

// 清掉早期版本留下的产物：16 个模板模型与 8 张占位贴图都已不再参与渲染
let removed = 0;
for (const { name } of combos) {
    const stale = join(BLOCK_DIR, `${name}.json`);
    if (existsSync(stale)) {
        rmSync(stale);
        removed++;
    }
}
if (existsSync(STALE_MARKER_DIR)) {
    rmSync(STALE_MARKER_DIR, { recursive: true });
    removed++;
}

// ---- 自检：角柱归属规则 ----

let failed = 0;
console.log('combo  柱数  角柱分布                     结果');
for (const { present, name } of combos) {
    const corners = [];
    for (const dir of ORDER) {
        if (!present.has(dir)) continue;
        corners.push(CORNER_OF[dir].owned);
        if (!present.has(FACINGS[dir].sharedOwner)) corners.push(CORNER_OF[dir].shared);
    }
    const unique = new Set(corners);
    const dup = corners.length !== unique.size;
    if (dup) failed++;
    console.log(
        `${name.padEnd(7)}${String(corners.length).padEnd(6)}${[...unique].sort().join(',').padEnd(30)}${dup ? '重复角柱!' : 'ok'}`,
    );
}

// ---- 校验：Java 几何表必须与本脚本算出的旋转结果逐位一致 ----

const javaPath = join(process.cwd(), 'common/src/main/java/marrydream/marisdecoration/block/utils/GuardrailParts.java');
const javaSource = readFileSync(javaPath, 'utf8');
const ENUM = { north: 'NORTH', east: 'EAST', south: 'SOUTH', west: 'WEST' };

let bad = 0;
const check = (cond, msg) => {
    if (!cond) {
        console.error(`  ✗ ${msg}`);
        bad++;
    }
};

for (const dir of ORDER) {
    const { rot, sharedOwner } = FACINGS[dir];
    const rail = rotateBox(BASE.rails[0], rot);
    const owned = rotateBox(BASE.postOwned, rot);
    const shared = rotateBox(BASE.postShared, rot);

    // 取该方向的代码片段，避免用 includes 时被别的方向同名坐标误判为通过
    const segment = (javaSource.split(`PARTS.put(Direction.${ENUM[dir]},`)[1] ?? '').split('SHARED_OWNER.put')[0];

    const railsMatch = segment.match(/rails\(([^)]*)\)/);
    const boxes = [...segment.matchAll(/box\(([^)]*)\)/g)].map((m) => m[1].split(',').map((s) => Number(s.trim())));

    const expectedRails = [rail[0], rail[2], rail[3], rail[5]];
    const actualRails = railsMatch ? railsMatch[1].split(',').map((s) => Number(s.trim())) : [];
    check(JSON.stringify(actualRails) === JSON.stringify(expectedRails),
        `Java ${dir}: rails 应为 (${expectedRails.join(', ')})，实际 (${actualRails.join(', ') || '缺失'})`);

    check(boxes.length === 2, `Java ${dir}: 应有两个 box（自有柱 + 共享柱），实际 ${boxes.length} 个`);
    check(JSON.stringify(boxes[0]) === JSON.stringify(owned),
        `Java ${dir}: 自有柱应为 (${owned.join(', ')})，实际 (${(boxes[0] ?? []).join(', ') || '缺失'})`);
    check(JSON.stringify(boxes[1]) === JSON.stringify(shared),
        `Java ${dir}: 共享柱应为 (${shared.join(', ')})，实际 (${(boxes[1] ?? []).join(', ') || '缺失'})`);

    check(javaSource.includes(`SHARED_OWNER.put(Direction.${ENUM[dir]}, Direction.${ENUM[sharedOwner]})`),
        `Java ${dir}: 共享柱归属应为 ${ENUM[sharedOwner]}`);
}

if (failed > 0 || bad > 0) {
    console.error(`\n失败：重复角柱 ${failed} 处，Java 几何表问题 ${bad} 处`);
    process.exit(1);
}
console.log(`\n完成：blockstate + 物品模型已生成（清理旧产物 ${removed} 项）；角柱无重复，Java 几何表一致。`);
