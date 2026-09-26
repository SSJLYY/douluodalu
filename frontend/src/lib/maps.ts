/**
 * 地图名表（与后端 MAP_NAMES 同步：索引 0-10 = profile.currentMapId，后端 MAX_MAP_ID 已扩至 10）。
 * 单一事实源：主页（game/page.tsx）按 currentMapId 取名；百科页（game/wiki/page.tsx）地图分类
 * 的名称/等级描述仍为独立条目。Next.js 路由文件不允许导出额外符号（build 类型检查会失败），
 * 故从 page.tsx 抽到此 lib 模块，并供 mapNames.test.ts 钉住契约防漂移。
 */
export const MAP_NAMES = [
    '圣魂村', '诺丁城外', '星斗外围', '落日森林', '极北之地', '海神岛', '杀戮之都外域', '神界废墟',
    '神王殿', '至高神庭', '创世之巅',
];
