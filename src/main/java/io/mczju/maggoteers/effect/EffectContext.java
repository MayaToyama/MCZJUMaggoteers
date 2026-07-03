package io.mczju.maggoteers.effect;

import java.util.HashMap;
import java.util.Map;

/** Effect 的类型化参数容器。put/get 链式。 */
public final class EffectContext {
    private final Map<EffectKey<?>, Object> data = new HashMap<>();

    public <T> EffectContext put(EffectKey<T> key, T val) { data.put(key, val); return this; }

    @SuppressWarnings("unchecked")
    public <T> T get(EffectKey<T> key) { return (T) data.get(key); }

    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(EffectKey<T> key, T def) {
        Object v = data.get(key);
        return v == null ? def : (T) v;
    }

    public boolean has(EffectKey<?> key) { return data.containsKey(key); }

    /** 返回浅拷贝（内部 map 独立），避免修改污染共享实例。 */
    public EffectContext copy() {
        EffectContext c = new EffectContext();
        c.data.putAll(this.data);
        return c;
    }
}
