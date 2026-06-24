import {
    battle,
    breakthrough,
    buyShopItem,
    challengeGuildBoss,
    claimOfflineReward,
    createGuild,
    cultivate,
    donateToGuild,
    equipBone,
    equipCore,
    equipRing,
    getBearerToken,
    getBossShopItems,
    getGameState,
    getGuildShopItems,
    getLimitedShopItems,
    getRankByLevel,
    getRankByTower,
    getUserGuild,
    getUserByToken,
    joinGuild,
    leaveGuild,
    listGuilds,
    sellBackpackItem,
    towerBattle,
    unequipBone,
    unequipCore,
    unequipRing,
    upgradeTalent,
} from '@/lib/server/auth-store';

type RouteContext = {
    params: Promise<{ path: string[] }>;
};

export async function GET(request: Request, context: RouteContext) {
    const user = getUserByToken(getBearerToken(request));
    if (!user) return unauthorized();

    const path = await getPath(context);
    if (path === 'game/state' || path === 'equipment') {
        return Response.json(getGameState(user.userId));
    }
    if (path === 'rank/level' || path === 'rank/tower') {
        return path === 'rank/level' ? Response.json(getRankByLevel()) : Response.json(getRankByTower());
    }
    if (path === 'shop/boss') {
        return Response.json(getBossShopItems());
    }
    if (path === 'shop/limited') {
        return Response.json(getLimitedShopItems());
    }
    if (path === 'guild/shop') {
        return Response.json(getGuildShopItems());
    }
    if (path === 'guild/list') {
        return Response.json(listGuilds());
    }
    if (path === 'guild/my') {
        return Response.json(getUserGuild(user.userId));
    }

    return Response.json({ message: '接口不存在' }, { status: 404 });
}

export async function POST(request: Request, context: RouteContext) {
    const user = getUserByToken(getBearerToken(request));
    if (!user) return unauthorized();

    const path = await getPath(context);
    if (path === 'action/cultivate') {
        return Response.json(cultivate(user.userId));
    }
    if (path === 'action/breakthrough') {
        return Response.json(breakthrough(user.userId));
    }
    if (path === 'action/battle') {
        return Response.json(battle(user.userId));
    }
    if (path.startsWith('shop/boss/buy/')) {
        const result = buyShopItem(user.userId, Number(path.slice('shop/boss/buy/'.length)), 'boss');
        if ('error' in result) return Response.json({ message: result.error }, { status: 400 });
        return Response.json(result);
    }
    if (path.startsWith('shop/limited/buy/')) {
        const result = buyShopItem(user.userId, Number(path.slice('shop/limited/buy/'.length)), 'limited');
        if ('error' in result) return Response.json({ message: result.error }, { status: 400 });
        return Response.json(result);
    }
    if (path.startsWith('guild/shop/buy/')) {
        const result = buyShopItem(user.userId, Number(path.slice('guild/shop/buy/'.length)), 'guild');
        if ('error' in result) return Response.json({ message: result.error }, { status: 400 });
        return Response.json(result);
    }
    if (path === 'guild/create') {
        const body = await request.json().catch(() => null) as { name?: string } | null;
        const result = createGuild(user.userId, body?.name ?? '');
        if ('error' in result) {
            return Response.json({ message: result.error }, { status: 400 });
        }
        return Response.json({ message: '宗门创建成功', guild: result.guild });
    }
    if (path.startsWith('guild/join/')) {
        const guildId = Number(path.slice('guild/join/'.length));
        const result = joinGuild(user.userId, guildId);
        if ('error' in result) {
            return Response.json({ message: result.error }, { status: 400 });
        }
        return Response.json({ message: '加入宗门成功', guild: result.guild });
    }
    if (path === 'guild/leave') {
        const result = leaveGuild(user.userId);
        if ('error' in result) {
            return Response.json({ message: result.error }, { status: 400 });
        }
        return Response.json(result);
    }
    if (path === 'guild/donate') {
        const body = await request.json().catch(() => null) as { amount?: number } | null;
        const result = donateToGuild(user.userId, Number(body?.amount ?? 0));
        if ('error' in result) return Response.json({ message: result.error }, { status: 400 });
        return Response.json(result);
    }
    if (path === 'guild/boss/challenge') {
        const result = challengeGuildBoss(user.userId);
        if ('error' in result) return Response.json({ message: result.error }, { status: 400 });
        return Response.json(result);
    }
    if (path === 'action/tower') {
        return Response.json(towerBattle(user.userId));
    }
    if (path.startsWith('talent/upgrade/')) {
        const branch = path.slice('talent/upgrade/'.length);
        const result = upgradeTalent(user.userId, branch);
        if ('error' in result) return Response.json({ message: result.error }, { status: 400 });
        return Response.json(result);
    }
    if (path === 'equipment/backpack/sell') {
        const body = await request.json().catch(() => null) as { backpackItemId?: number } | null;
        const result = sellBackpackItem(user.userId, Number(body?.backpackItemId ?? 0));
        if ('error' in result) return Response.json({ message: result.error }, { status: 400 });
        return Response.json(result);
    }
    if (path === 'game/offline-claim') {
        return Response.json(claimOfflineReward(user.userId));
    }
    if (path === 'equipment/backpack/expand') {
        return Response.json({ message: '背包扩容功能暂未开放' });
    }
    if (path === 'equip/ring') {
        const body = await request.json().catch(() => null) as { backpackItemId?: number; slotIndex?: number } | null;
        const result = equipRing(user.userId, Number(body?.backpackItemId ?? 0), Number(body?.slotIndex ?? 0));
        if ('error' in result) return Response.json({ message: result.error }, { status: 400 });
        return Response.json(result);
    }
    if (path === 'equip/bone') {
        const body = await request.json().catch(() => null) as { backpackItemId?: number; slotIndex?: number } | null;
        const result = equipBone(user.userId, Number(body?.backpackItemId ?? 0), Number(body?.slotIndex ?? 0));
        if ('error' in result) return Response.json({ message: result.error }, { status: 400 });
        return Response.json(result);
    }
    if (path === 'equip/core') {
        const body = await request.json().catch(() => null) as { backpackItemId?: number; slotType?: string } | null;
        const result = equipCore(user.userId, Number(body?.backpackItemId ?? 0), body?.slotType ?? '');
        if ('error' in result) return Response.json({ message: result.error }, { status: 400 });
        return Response.json(result);
    }
    if (path === 'unequip/ring') {
        const body = await request.json().catch(() => null) as { slotIndex?: number } | null;
        const result = unequipRing(user.userId, Number(body?.slotIndex ?? 0));
        if ('error' in result) return Response.json({ message: result.error }, { status: 400 });
        return Response.json(result);
    }
    if (path === 'unequip/bone') {
        const body = await request.json().catch(() => null) as { slotIndex?: number } | null;
        const result = unequipBone(user.userId, Number(body?.slotIndex ?? 0));
        if ('error' in result) return Response.json({ message: result.error }, { status: 400 });
        return Response.json(result);
    }
    if (path === 'unequip/core') {
        const body = await request.json().catch(() => null) as { slotType?: string } | null;
        const result = unequipCore(user.userId, body?.slotType ?? '');
        if ('error' in result) return Response.json({ message: result.error }, { status: 400 });
        return Response.json(result);
    }

    return Response.json({ message: '接口不存在' }, { status: 404 });
}

async function getPath(context: RouteContext) {
    const { path } = await context.params;
    return path.join('/');
}

function unauthorized() {
    return Response.json({ message: '认证失败，请重新登录' }, { status: 401 });
}
