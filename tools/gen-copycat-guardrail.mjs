/**
 * 生成 copycat_guardrail 的 16 个方块模型 + blockstate。
 *
 * 几何全部来自 black_steel_guardrail 的 guardrail/straight.json（单面模型），
 * 不使用拐角模型（inner/outer），也不涉及任何自动连接逻辑。
 *
 * 角柱归属规则（关键）：
 *   每个方向的单面模型自带两根端柱——一根位于它「拥有」的角落，另一根位于与
 *   相邻方向「共享」的角落。若直接四向叠加，共享角落会出现两份完全重合的几何体
 *   导致 z-fighting。规则如下：
 *     拥有柱  : 本方向存在即绘制
 *     共享柱  : 本方向存在 且 该角落的归属方向不存在时才绘制
 *   角落实例归属：NE→north, SE→east, SW→south, NW→west
 *
 * 由此得到（可对照脚本末尾的自检输出）：
 *   仅东面      -> 2 根柱（东北由东补画、东南自有）
 *   仅北+南     -> 4 根柱，四角各一，无缺柱
 *   东+南       -> 3 根柱（东南只画一次）
 *   四面全放    -> 4 根柱，每面各贡献自己拥有的那一根
 *
 * 用法： node tools/gen-copycat-guardrail.mjs
 */

import { writeFileSync, mkdirSync, readFileSync, existsSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';

const NS = 'maris-decoration';
const BLOCK_DIR = join(process.cwd(), 'src/main/resources/assets', NS, 'models/block/copycat_guardrail');
const BLOCKSTATE = join(process.cwd(), 'src/main/resources/assets', NS, 'blockstates/copycat_guardrail.json');
const ITEM_MODEL = join(process.cwd(), 'src/main/resources/assets', NS, 'models/item/copycat_guardrail.json');

// 占位贴图：这两张在运行时会被替换成伪装材质，但它们必须是两张不同的图，
// 否则渲染层无法区分「柱」和「横梁」两个槽位。
const COLUMN_MARKER = `${NS}:block/steel_block`;
const ROW_MARKER = `${NS}:block/black_steel_block`;
// 未伪装时的默认外观，与 Create 的 copycat 保持一致。
const DEFAULT_TEXTURE = 'create:block/copycat_base';

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

/** 某个方向在本组合下的几何贡献。 */
function contributions(dir, present) {
    const { rot, sharedOwner } = FACINGS[dir];
    const out = [];
    for (const rail of BASE.rails) out.push({ box: rotateBox(rail, rot), slot: 'row' });
    out.push({ box: rotateBox(BASE.postOwned, rot), slot: 'column' });
    // 共享柱：只有归属方向缺席时才补画，避免与对方重合。
    if (!present.has(sharedOwner)) {
        out.push({ box: rotateBox(BASE.postShared, rot), slot: 'column' });
    }
    return out;
}

/** 返回该组合的全部几何体，附带用于自检的角柱坐标。 */
function buildParts(present) {
    const parts = [];
    for (const dir of ORDER) {
        if (present.has(dir)) parts.push(...contributions(dir, present));
    }
    return parts;
}

function comboName(present) {
    if (present.size === 0) return 'none';
    return ORDER.filter((d) => present.has(d)).map((d) => LETTER[d]).join('');
}

function modelJson(present, textures) {
    const parts = buildParts(present);
    return JSON.stringify(
        {
            parent: `${NS}:block/thin_side_block`,
            textures: { particle: '#row', column: textures.column, row: textures.row },
            elements: parts.map((p) => toElement(p.box, p.slot === 'column' ? '#column' : '#row')),
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
    const name = comboName(present);
    combos.push({ present, name });

    const key = ORDER.map((d) => `${d}=${present.has(d)}`).join(',');
    if (present.size === 0) {
        // 四面全无是空壳状态。正常玩法到不了（放置至少给一面，扳手也不允许拆掉最后一面），
        // 但 /setblock 之类仍可能造出来。直接指向空气模型，与 Create 的 copycat blockstate 一致，
        // 避免依赖「elements: [] 是否被模型加载器接受」这种不确定行为。
        variants[key] = { model: 'minecraft:block/air' };
        continue;
    }

    writeFileSync(join(BLOCK_DIR, `${name}.json`), modelJson(present, { column: COLUMN_MARKER, row: ROW_MARKER }));
    variants[key] = { model: `${NS}:block/copycat_guardrail/${name}` };
}

// 清掉早期版本留下的 none.json，它既不再被引用，也不该留在资源里
if (existsSync(join(BLOCK_DIR, 'none.json'))) {
    rmSync(join(BLOCK_DIR, 'none.json'));
}

writeFileSync(BLOCKSTATE, JSON.stringify({ variants }, null, 4) + '\n');

// 物品栏模型：没有 BlockEntity，必须走静态模型，直接用 Create 的默认伪装贴图。
// 只放一个面（东面 = 基准朝向），与 black_steel_guardrail 的物品模型保持一致，
// 方向与格子内的位置才对得上。四面全放会让物品图标看起来是一整圈，很怪。
mkdirSync(dirname(ITEM_MODEL), { recursive: true });
writeFileSync(join(BLOCK_DIR, 'item.json'), modelJson(new Set(['east']), { column: DEFAULT_TEXTURE, row: DEFAULT_TEXTURE }));
writeFileSync(ITEM_MODEL, JSON.stringify({ parent: `${NS}:block/copycat_guardrail/item` }, null, 4) + '\n');

// ---- 自检 ----

const CORNER_OF = {
    east: { owned: 'SE', shared: 'NE' },
    south: { owned: 'SW', shared: 'SE' },
    west: { owned: 'NW', shared: 'SW' },
    north: { owned: 'NE', shared: 'NW' },
};

let failed = 0;
console.log('combo  柱数  角柱分布                     结果');
for (const { present, name } of combos) {
    const corners = [];
    for (const dir of ORDER) {
        if (!present.has(dir)) continue;
        const { rot, sharedOwner } = FACINGS[dir];
        corners.push(CORNER_OF[dir].owned);
        if (!present.has(sharedOwner)) corners.push(CORNER_OF[dir].shared);
    }
    const unique = new Set(corners);
    const dup = corners.length !== unique.size;
    if (dup) failed++;
    console.log(
        `${name.padEnd(7)}${String(corners.length).padEnd(6)}${[...unique].sort().join(',').padEnd(30)}${dup ? '重复角柱!' : 'ok'}`,
    );
}

// ---- 模型结构校验 ----
// 模型结构非法时客户端不会报错，只会渲染成紫黑格，所以这里静态把关。

const FACE_NAMES = ['down', 'up', 'north', 'south', 'west', 'east'];
let bad = 0;
const check = (cond, msg) => {
    if (!cond) {
        console.error(`  ✗ ${msg}`);
        bad++;
    }
};

// 只校验真正生成的模型（空壳状态指向 minecraft:block/air，不在此列）
for (const { name } of combos.filter((c) => c.present.size > 0)) {
    const model = JSON.parse(readFileSync(join(BLOCK_DIR, `${name}.json`), 'utf8'));
    check(model.textures?.column && model.textures?.row, `${name}: 缺少 column/row 贴图声明`);
    check(Array.isArray(model.elements) && model.elements.length > 0, `${name}: elements 缺失或为空`);
    for (const [i, el] of (model.elements ?? []).entries()) {
        const where = `${name}[${i}]`;
        check(Array.isArray(el.from) && el.from.length === 3, `${where}: from 非法`);
        check(Array.isArray(el.to) && el.to.length === 3, `${where}: to 非法`);
        for (let k = 0; k < 3; k++) {
            check(el.from[k] >= 0 && el.from[k] <= 16, `${where}: from[${k}]=${el.from[k]} 越界`);
            check(el.to[k] >= 0 && el.to[k] <= 16, `${where}: to[${k}]=${el.to[k]} 越界`);
            check(el.from[k] <= el.to[k], `${where}: from>to（轴 ${k}）`);
        }
        for (const face of FACE_NAMES) {
            const f = el.faces[face];
            check(!!f, `${where}: 缺少面 ${face}`);
            if (!f) continue;
            check(Array.isArray(f.uv) && f.uv.length === 4, `${where}.${face}: uv 必须是 4 个数`);
            check((f.uv ?? []).every((v) => typeof v === 'number' && v >= 0 && v <= 16),
                `${where}.${face}: uv 越界 ${JSON.stringify(f.uv)}`);
            check(f.texture === '#column' || f.texture === '#row', `${where}.${face}: 未知贴图槽 ${f.texture}`);
            if (f.cullface) {
                check(FACE_NAMES.includes(f.cullface), `${where}.${face}: cullface 非法 ${f.cullface}`);
            }
        }
    }
}

// 物品栏模型必须存在，否则物品会渲染成紫黑格
check(existsSync(join(BLOCK_DIR, 'item.json')), '缺少 item.json');
check(existsSync(ITEM_MODEL), '缺少 models/item/copycat_guardrail.json');

// blockstate 的每个变体都要指向真实存在的模型
const blockstate = JSON.parse(readFileSync(BLOCKSTATE, 'utf8'));
const variantKeys = Object.keys(blockstate.variants ?? {});
check(variantKeys.length === 16, `blockstate 变体数应为 16，实际 ${variantKeys.length}`);
for (const [key, variant] of Object.entries(blockstate.variants ?? {})) {
    if (!variant.model.startsWith(`${NS}:`)) {
        // 例如 minecraft:block/air，不需要在本 mod 资源里找
        continue;
    }
    const rel = variant.model.replace(`${NS}:block/copycat_guardrail/`, '');
    check(existsSync(join(BLOCK_DIR, `${rel}.json`)), `变体 ${key} 指向不存在的模型 ${variant.model}`);
}

// ---- Java 几何表校验 ----
// GuardrailParts.java 里的坐标是手写的，必须与本脚本算出的旋转结果逐位一致，
// 否则「命中柱/横梁判定」和「方块轮廓」会与实际渲染的模型对不上。

const javaPath = join(process.cwd(), 'src/main/java/marrydream/marisdecoration/block/utils/GuardrailParts.java');
const javaSource = readFileSync(javaPath, 'utf8');
const ENUM = { north: 'NORTH', east: 'EAST', south: 'SOUTH', west: 'WEST' };

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

    const javaOwner = javaSource.includes(
        `SHARED_OWNER.put(Direction.${ENUM[dir]}, Direction.${ENUM[sharedOwner]})`);
    check(javaOwner, `Java ${dir}: 共享柱归属应为 ${ENUM[sharedOwner]}（东北→北、东南→东、西南→南、西北→西）`);
}

if (failed > 0 || bad > 0) {
    console.error(`\n失败：重复角柱 ${failed} 处，模型/几何校验问题 ${bad} 处`);
    process.exit(1);
}
console.log(`\n完成：16 个模型 + blockstate 已生成；角柱无重复，模型结构校验通过，Java 几何表与模型一致。`);
