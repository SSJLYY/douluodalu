import { createSession, createUser, toAuthResponse } from '@/lib/server/auth-store';

export async function POST(request: Request) {
    const body = await request.json().catch(() => null) as {
        username?: string;
        password?: string;
        nickname?: string;
    } | null;

    const username = body?.username?.trim() ?? '';
    const password = body?.password ?? '';
    const nickname = body?.nickname?.trim() || username;

    if (username.length < 3) {
        return Response.json({ message: '用户名至少需要 3 个字符' }, { status: 400 });
    }
    if (password.length < 6) {
        return Response.json({ message: '密码至少需要 6 个字符' }, { status: 400 });
    }
    if (nickname.length < 2) {
        return Response.json({ message: '昵称至少需要 2 个字符' }, { status: 400 });
    }

    const user = createUser(username, password, nickname);
    if (!user) {
        return Response.json({ message: '用户名已存在' }, { status: 409 });
    }

    return Response.json(toAuthResponse(user, createSession(user.userId)));
}
