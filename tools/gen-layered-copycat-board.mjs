/**
 * 生成 layered_copycat_board 的 blockstate、物品模型与方块模型。
 *
 * 与 copycat_guardrail 同一套路子：几何不由模型文件描述，渲染时由客户端代码按方块实体的
 * 占用掩码 / 窗 / 角归属动态发射（见 LayeredCopycatBoardModel + LayeredBoardParts）。
 * 因此 blockstate 的<b>全部</b>变体都指向 minecraft:block/air —— Create 自己的伪装方块也一样。
 *
 * 物品模型没有方块实体可用，只能走静态模型：一块 1px 厚、16×16 的板，
 * 贴图用 Create 伪装板的底材（与护栏的物品模型一致）。
 *
 * 用法： node tools/gen-layered-copycat-board.mjs
 */

import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';

const NS = 'maris-decoration';
const ASSETS = join(process.cwd(), 'src/main/resources/assets', NS);
const BLOCK_DIR = join(ASSETS, 'models/block/layered_copycat_board');
const BLOCKSTATE = join(ASSETS, 'blockstates/layered_copycat_board.json');
const ITEM_MODEL = join(ASSETS, 'models/item/layered_copycat_board.json');

/** 未伪装时的默认外观，与 Create 的 copycat 保持一致。 */
const DEFAULT_TEXTURE = 'create:block/copycat_base';

/** UP 板在方块本地坐标里的位置（0..16）。 */
const PLATE = { from: [0, 15, 0], to: [16, 16, 16] };

/**
 * 计算一个长方体六个面的 UV，遵循 Minecraft 的投影约定。
 * 与 tools/gen-copycat-guardrail.mjs 里的实现一致。
 */
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

/** 贴在方块边界上的面才加 cullface。 */
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

function element(from, to, texture) {
    const box = [...from, ...to];
    const faces = {};
    for (const face of ['down', 'up', 'north', 'south', 'west', 'east']) {
        const entry = { uv: faceUv(box, face), texture };
        const cull = cullFace(box, face);
        if (cull) entry.cullface = cull;
        faces[face] = entry;
    }
    return { from, to, faces };
}

// ---- blockstate：只有 waterlogged 一个属性，两个变体都指向空气 ----
mkdirSync(dirname(BLOCKSTATE), { recursive: true });
const variants = {};
for (const waterlogged of [false, true]) {
    variants[`waterlogged=${waterlogged}`] = { model: 'minecraft:block/air' };
}
writeFileSync(BLOCKSTATE, JSON.stringify({ variants }, null, 4) + '\n');

// ---- 方块模型（物品模型引用它）----
mkdirSync(BLOCK_DIR, { recursive: true });
writeFileSync(
    join(BLOCK_DIR, 'item.json'),
    JSON.stringify(
        {
            parent: `${NS}:block/thin_side_block`,
            textures: { particle: '#all', all: DEFAULT_TEXTURE },
            elements: [element(PLATE.from, PLATE.to, '#all')],
        },
        null,
        4,
    ) + '\n',
);

mkdirSync(dirname(ITEM_MODEL), { recursive: true });
writeFileSync(ITEM_MODEL, JSON.stringify({ parent: `${NS}:block/layered_copycat_board/item` }, null, 4) + '\n');

console.log('完成：layered_copycat_board 的 blockstate + 物品模型已生成。');
