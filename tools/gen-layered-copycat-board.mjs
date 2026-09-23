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

/**
 * 物品形态用的板位（0..16）：一块贴在方块底部的 1px 水平板。
 *
 * <p>位置与贴图 UV 刻意与 Copycats+ 的 {@code copycat_base/board} 完全一致，
 * 好让物品栏里的图标看起来一样。
 */
const ITEM_PLATE = { from: [0, 0, 0], to: [16, 1, 16] };

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

// 物品形态的模型不加 cullface，所以这里不需要 cullFace / element 两个辅助函数。

// ---- blockstate：只有 waterlogged 一个属性，两个变体都指向空气 ----
mkdirSync(dirname(BLOCKSTATE), { recursive: true });
const variants = {};
for (const waterlogged of [false, true]) {
    variants[`waterlogged=${waterlogged}`] = { model: 'minecraft:block/air' };
}
writeFileSync(BLOCKSTATE, JSON.stringify({ variants }, null, 4) + '\n');

// ---- 物品形态的方块模型（物品模型引用它）----
//
// 只服务物品栏 / 手持：世界里的外观由 LayeredCopycatBoardModel 动态发射，与这里无关。
// parent 取原版 block/block，是为了直接继承它标准的 gui / firstperson / thirdperson
// display 变换——不要换成带自定义 display 的模型，否则图标的角度和大小都会与参考不一致。
// 六个面都不加 cullface：物品渲染不做邻块剔除，加了只会和参考模型产生差异。
const faceNames = ['down', 'up', 'north', 'south', 'west', 'east'];
const itemBox = [...ITEM_PLATE.from, ...ITEM_PLATE.to];
const itemFaces = {};
for (const face of faceNames) {
    itemFaces[face] = { uv: faceUv(itemBox, face), texture: '#all' };
}
mkdirSync(BLOCK_DIR, { recursive: true });
writeFileSync(
    join(BLOCK_DIR, 'item.json'),
    JSON.stringify(
        {
            parent: 'block/block',
            textures: { particle: '#all', all: DEFAULT_TEXTURE },
            elements: [{ from: ITEM_PLATE.from, to: ITEM_PLATE.to, faces: itemFaces }],
        },
        null,
        4,
    ) + '\n',
);

mkdirSync(dirname(ITEM_MODEL), { recursive: true });
writeFileSync(ITEM_MODEL, JSON.stringify({ parent: `${NS}:block/layered_copycat_board/item` }, null, 4) + '\n');

console.log('完成：layered_copycat_board 的 blockstate + 物品模型已生成。');
