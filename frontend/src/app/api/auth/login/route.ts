import { createSession, findUserByUsername, toAuthResponse, verifyPassword } from '@/lib/server/auth-store';

export async function POST(request: Request) {
    const body = await request.json().catch(() => null) as {
        username?: string;
        password?: string;
    } | null;

    const username = body?.username?.trim() ?? '';
    const password = body?.password ?? '';
    const user = findUserByUsername(username);

    if (!user || !verifyPassword(password, user)) {
        return Response.json({ message: '用户名或密码错误' }, { status: 401 });
    }

    return Response.json(toAuthResponse(user, createSession(user.userId)));
}
