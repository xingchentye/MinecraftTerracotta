package com.endercore.core.comm.protocol;

import com.endercore.core.comm.exception.CoreProtocolException;

/**
 * kind 字符串校验与工具方法。
 */
public final class CoreKinds {
    private CoreKinds() {
    }

    /**
     * 校验 kind 是否符合 namespace:path 形式。
     *
     * @param kind kind
     */
    public static void validate(String kind) {
        if (kind == null || kind.isEmpty()) {
            throw new CoreProtocolException("kind 不能为空");
        }
        int idx = kind.indexOf(':');
        if (idx <= 0 || idx >= kind.length() - 1) {
            throw new CoreProtocolException("kind 必须为 namespace:path: " + kind);
        }
        if (kind.indexOf(':', idx + 1) != -1) {
            throw new CoreProtocolException("kind 只能包含一个冒号: " + kind);
        }
    }
}
