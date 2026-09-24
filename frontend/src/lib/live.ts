'use client';

import { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';

/** 后端 /topic/battle 广播体（WebSocketService.broadcastBattleResult，含所有用户的战斗） */
export interface BattleEvent {
    userId: number;
    username: string;
    monsterName: string;
    won: boolean;
    timestamp: number;
}

const BATTLE_TOPIC = '/topic/battle';

/** 由 NEXT_PUBLIC_API_URL 推导 ws 地址（http→ws / https→wss），指向后端原生 STOMP 端点 */
function stompBrokerUrl(): string {
    const base = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
    return `${base.replace(/^http/, 'ws').replace(/\/+$/, '')}/ws-native`;
}

let client: Client | null = null;
let connected = false;
let watchers = 0;
const battleHandlers = new Set<(ev: BattleEvent) => void>();
const statusHandlers = new Set<(on: boolean) => void>();

function setConnected(on: boolean) {
    if (connected === on) return;
    connected = on;
    statusHandlers.forEach((h) => h(on));
}

/**
 * 单例 STOMP 连接（引用计数）：第一个 useLiveBattle 挂载时建立，全部卸载后断开。
 * 心跳与断线重连间隔用 @stomp/stompjs 库默认值；重连成功后 onConnect 会自动重新订阅。
 * WS 不可用（后端未启动/网络断开）时库持续静默重连，useLiveBattle 返回 false，
 * 由调用方（useGameData 等）回落到纯轮询，不影响功能。
 */
function acquire(): () => void {
    if (typeof window === 'undefined') return () => undefined;
    watchers += 1;
    if (!client) {
        const c = new Client({ brokerURL: stompBrokerUrl() });
        c.onConnect = () => {
            setConnected(true);
            c.subscribe(BATTLE_TOPIC, (frame) => {
                try {
                    const ev = JSON.parse(frame.body) as BattleEvent;
                    battleHandlers.forEach((h) => h(ev));
                } catch (err) {
                    console.error('解析战斗广播消息失败:', err);
                }
            });
        };
        c.onWebSocketClose = () => setConnected(false);
        c.onStompError = (frame) => console.error('STOMP 协议错误:', frame.headers['message']);
        c.activate();
        client = c;
    }
    let released = false;
    return () => {
        if (released) return;
        released = true;
        watchers -= 1;
        if (watchers <= 0 && client) {
            const c = client;
            client = null;
            setConnected(false);
            void c.deactivate();
        }
    };
}

/**
 * 订阅 /topic/battle 广播（多用户共用一条连接），并返回 WS 当前是否在线。
 * 后端把所有用户的战斗都广播到同一话题，调用方必须自行按 userId 过滤或按需消费。
 */
export function useLiveBattle(handler: (ev: BattleEvent) => void): boolean {
    const [isConnected, setIsConnected] = useState(connected);
    const handlerRef = useRef(handler);
    useEffect(() => {
        handlerRef.current = handler;
    });
    useEffect(() => {
        const release = acquire();
        const onBattle = (ev: BattleEvent) => handlerRef.current(ev);
        const onStatus = (on: boolean) => setIsConnected(on);
        battleHandlers.add(onBattle);
        statusHandlers.add(onStatus);
        return () => {
            battleHandlers.delete(onBattle);
            statusHandlers.delete(onStatus);
            release();
        };
    }, []);
    return isConnected;
}
