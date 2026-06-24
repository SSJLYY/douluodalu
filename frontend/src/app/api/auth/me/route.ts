import { getBearerToken, getUserByToken, toUserInfo } from '@/lib/server/auth-store';

export async function GET(request: Request) {
    const user = getUserByToken(getBearerToken(request));
    if (!user) {
        return Response.json({ message: '认证失败，请重新登录' }, { status: 401 });
    }

    return Response.json(toUserInfo(user));
}
