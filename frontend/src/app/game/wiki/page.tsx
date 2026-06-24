'use client';

import { useState } from 'react';

const WIKI_CATEGORIES = [
    {
        id: 'realms',
        title: '境界系统',
        icon: '⚡',
        content: [
            { name: '魂士', level: 1, description: '初始境界，刚觉醒武魂' },
            { name: '魂师', level: 2, description: '获得第一个魂环' },
            { name: '大魂师', level: 3, description: '获得第二个魂环' },
            { name: '魂尊', level: 4, description: '获得第三个魂环' },
            { name: '魂宗', level: 5, description: '获得第四个魂环' },
            { name: '魂王', level: 6, description: '获得第五个魂环' },
            { name: '魂帝', level: 7, description: '获得第六个魂环' },
            { name: '魂圣', level: 8, description: '获得第七个魂环' },
            { name: '魂斗罗', level: 9, description: '获得第八个魂环' },
            { name: '封号斗罗', level: 10, description: '获得第九个魂环' },
            { name: '极限斗罗', level: 11, description: '突破人类极限' },
            { name: '半神', level: 12, description: '半神之境' },
            { name: '神祇', level: 13, description: '成为神祇' },
            { name: '神王', level: 14, description: '神王之境' },
            { name: '至高神王', level: 15, description: '至高神王' },
            { name: '创世神', level: 16, description: '创世神之境' },
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
            { name: '攻击魂核', type: 'ATTACK', description: '提升攻击相关属性' },
            { name: '防御魂核', type: 'DEFENSE', description: '提升防御相关属性' },
            { name: '辅助魂核', type: 'UTILITY', description: '提升辅助相关属性' },
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

            {/* 分类标签 */}
            <div className="flex flex-wrap gap-2">
                {WIKI_CATEGORIES.map((category) => (
                    <button
                        key={category.id}
                        onClick={() => setActiveCategory(category.id)}
                        className={`px-4 py-2 rounded-lg text-sm font-semibold transition-colors ${
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
                                <div className="flex justify-between items-start">
                                    <div>
                                        <h3 className="font-semibold text-lg">{item.name}</h3>
                                        <p className="text-gray-400 mt-1">{item.description}</p>
                                    </div>
                                    {'level' in item && (
                                        <span className="bg-yellow-600 px-2 py-1 rounded text-sm">
                                            等级 {item.level}
                                        </span>
                                    )}
                                    {'year' in item && (
                                        <span className="bg-purple-600 px-2 py-1 rounded text-sm">
                                            {item.year}年
                                        </span>
                                    )}
                                    {'slot' in item && (
                                        <span className="bg-blue-600 px-2 py-1 rounded text-sm">
                                            槽位 {item.slot + 1}
                                        </span>
                                    )}
                                    {'type' in item && (
                                        <span className="bg-green-600 px-2 py-1 rounded text-sm">
                                            {item.type}
                                        </span>
                                    )}
                                    {'id' in item && !('year' in item) && !('slot' in item) && !('type' in item) && (
                                        <span className="bg-gray-600 px-2 py-1 rounded text-sm">
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
                    <li>• 了解装备系统，选择合适装备</li>
                    <li>• 了解地图系统，选择合适修炼地点</li>
                </ul>
            </div>
        </div>
    );
}