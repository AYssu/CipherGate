package com.ayssu.ciphergate.agent;

public final class AgentContextHolder {
    private static final ThreadLocal<AgentContext> TL = new ThreadLocal<>();

    private AgentContextHolder() {
    }

    public static AgentContext get() {
        return TL.get();
    }

    public static void set(AgentContext ctx) {
        TL.set(ctx);
    }

    public static void clear() {
        TL.remove();
    }
}

