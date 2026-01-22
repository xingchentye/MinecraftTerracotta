package com.endercore.core.comm.protocol;

import com.endercore.core.comm.exception.CoreProtocolException;

 
/**
 * 协议类型工具类。
 * 用于验证协议类型字符串的格式。
 *
 * @author Ender Developer
 * @version 1.0
 * @since 1.0
 */
public final class CoreKinds {
    /**
     * 私有构造函数，防止实例化。
     */
    private CoreKinds() {
    }

    /**
     * 验证协议类型字符串是否合法。
     * 格式必须为 namespace:path。
     *
     * @param kind 协议类型字符串
     * @throws CoreProtocolException 当格式无效时抛出
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
