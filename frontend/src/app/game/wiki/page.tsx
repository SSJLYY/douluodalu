'use client';

import { useState } from 'react';

const WIKI_CATEGORIES = [
    {
        id: 'realms',
        title: '境界系统',
        icon: '⚡',
        content: [
            // 真实口径：16 境界每 10 级一档，level 为该境界的起始等级（与后端境界换算同源）
            { name: '魂士', level: 1, description: '初始境界，刚觉醒武魂' },
            { name: '魂师', level: 11, description: '获得第一个魂环' },
            { name: '大魂师', level: 21, description: '获得第二个魂环' },
            { name: '魂尊', level: 31, description: '获得第三个魂环' },
            { name: '魂宗', level: 41, description: '获得第四个魂环' },
            { name: '魂王', level: 51, description: '获得第五个魂环' },
            { name: '魂帝', level: 61, description: '获得第六个魂环' },
            { name: '魂圣', level: 71, description: '获得第七个魂环' },
            { name: '魂斗罗', level: 81, description: '获得第八个魂环' },
            { name: '封号斗罗', level: 91, description: '获得第九个魂环' },
            { name: '极限斗罗', level: 101, description: '突破人类极限' },
            { name: '半神', level: 111, description: '半神之境' },
            { name: '神祇', level: 121, description: '成为神祇' },
            { name: '神王', level: 131, description: '神王之境' },
            { name: '至高神王', level: 141, description: '至高神王' },
            { name: '创世神', level: 151, description: '创世神之境' },
            { name: '突破消耗', description: '每次突破消耗魂力 120×等级^1.55（Lv.1 需 120，Lv.50 约需 5.16 万）' },
        ]
    },
    {
        id: 'prestige',
        title: '转生系统',
        icon: '🔄',
        content: [
            { name: '神位传承', description: '达到 Lv.50 后可在主页进行转生；转生不可逆，操作前会弹出确认清单供核对' },
            { name: '转生收益', description: '每转全属性 +10%、收入 +10%；每次转生额外获得 1 点天赋点' },
            { name: '重置项', description: '等级回到 Lv.1、金币清零、魂力清零、已装备魂环/魂骨卸回背包' },
            { name: '保留项', description: '装备与背包、天赋等级（并 +1 天赋点）、成就、杀戮之都进度、推图进度、图鉴、Boss币' },
            { name: '与天赋联动', description: '转生获得的天赋点在「天赋」页（/game/talent）分配，永久强化角色属性' },
            { name: '与成就联动', description: 'prestige 类成就在「成就」页（/game/achievements）按累计转生次数解锁，属性奖励永久生效' },
        ]
    },
    {
        id: 'rings',
        title: '魂环系统',
        icon: '💍',
        content: [
            { name: '百年魂环', year: 100, description: '黄色，基础魂环' },
            { name: '千年魂环', year: 1000, description: '紫色，稀有魂环' },
            { name: '万年魂环', year: 10000, description: '黑色，珍贵魂环' },
            { name: '十万年魂环', year: 100000, description: '红色，传说魂环' },
            { name: '百万年魂环', year: 1000000, description: '金色，神话魂环' },
        ]
    },
    {
        id: 'bones',
        title: '魂骨系统',
        icon: '🦴',
        content: [
            { name: '头骨', slot: 0, description: '提升精神力' },
            { name: '左臂骨', slot: 1, description: '提升攻击力' },
            { name: '右臂骨', slot: 2, description: '提升攻击力' },
            { name: '躯干骨', slot: 3, description: '提升生命值' },
            { name: '左腿骨', slot: 4, description: '提升速度' },
            { name: '右腿骨', slot: 5, description: '提升速度' },
        ]
    },
    {
        id: 'cores',
        title: '魂核系统',
        icon: '💎',
        content: [
            { name: '左魂核', slot: 0, description: '左侧魂核槽位，装备任意魂核提供被动加成' },
            { name: '右魂核', slot: 1, description: '右侧魂核槽位，装备任意魂核提供被动加成' },
        ]
    },
    {
        id: 'maps',
        title: '地图系统',
        icon: '🗺️',
        content: [
            { name: '圣魂村', id: 0, description: '新手村，适合1-10级' },
            { name: '诺丁城外', id: 1, description: '适合11-20级' },
            { name: '星斗外围', id: 2, description: '适合21-30级' },
            { name: '落日森林', id: 3, description: '适合31-40级' },
            { name: '极北之地', id: 4, description: '适合41-50级' },
            { name: '海神岛', id: 5, description: '适合51-60级' },
            { name: '杀戮之都外域', id: 6, description: '适合61-70级' },
            { name: '神界废墟', id: 7, description: '适合71-80级' },
        ]
    },
];

export default function WikiPage() {
    const [activeCategory, setActiveCategory] = useState('realms');

    const currentCategory = WIKI_CATEGORIES.find(c => c.id === activeCategory);

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-bold text-yellow-400">百科</h1>

            {/* 分类标签（6 个 tab：375px 下用横向滚动收纳为一行，避免换行挤压内容区） */}
            <div className="flex gap-2 dl-scroll-x pb-1">
                {WIKI_CATEGORIES.map((category) => (
                    <button
                        key={category.id}
                        type="button"
                        aria-pressed={activeCategory === category.id}
                        onClick={() => setActiveCategory(category.id)}
                        className={`px-4 min-h-11 shrink-0 whitespace-nowrap rounded-lg text-sm font-semibold transition-colors inline-flex items-center ${
                            activeCategory === category.id
                                ? 'bg-yellow-600 text-white'
                                : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
                        }`}
                    >
                        <span className="mr-2">{category.icon}</span>
                        {category.title}
                    </button>
                ))}
            </div>

            {/* 内容显示 */}
            {currentCategory && (
                <div className="bg-gray-800 rounded-lg p-6">
                    <h2 className="text-xl font-semibold mb-4 flex items-center gap-2">
                        <span>{currentCategory.icon}</span>
                        {currentCategory.title}
                    </h2>
                    
                    <div className="space-y-4">
                        {currentCategory.content.map((item, index) => (
                            <div key={index} className="bg-gray-700 rounded-lg p-4">
                                <div className="flex justify-between items-start gap-2">
                                    <div className="min-w-0">
                                        <h3 className="font-semibold text-lg break-words">{item.name}</h3>
                                        <p className="text-gray-400 mt-1">{item.description}</p>
                                    </div>
                                    {'level' in item && (
                                        <span className="bg-yellow-600 px-2 py-1 rounded text-sm shrink-0">
                                            Lv.{item.level}+
                                        </span>
                                    )}
                                    {'year' in item && (
                                        <span className="bg-purple-600 px-2 py-1 rounded text-sm shrink-0 tabular-nums">
                                            {item.year}年
                                        </span>
                                    )}
                                    {'slot' in item && (
                                        <span className="bg-blue-600 px-2 py-1 rounded text-sm shrink-0">
                                            槽位 {item.slot + 1}
                                        </span>
                                    )}
                                    {'id' in item && !('year' in item) && !('slot' in item) && (
                                        <span className="bg-gray-600 dl-badge-outline px-2 py-1 rounded text-sm shrink-0">
                                            地图 {item.id + 1}
                                        </span>
                                    )}
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* 百科说明 */}
            <div className="bg-gray-800 rounded-lg p-4">
                <h3 className="font-semibold mb-2">百科说明</h3>
                <ul className="text-sm text-gray-300 space-y-1">
                    <li>• 百科提供游戏内各种系统的详细介绍</li>
                    <li>• 了解境界系统，规划修炼路线</li>
                    <li>• 了解转生系统，规划长期成长路线</li>
                    <li>• 了解装备系统，选择合适装备</li>
                    <li>• 了解地图系统，选择合适修炼地点</li>
                </ul>
            </div>
        </div>
    );
}