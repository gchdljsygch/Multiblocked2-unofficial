package com.lowdragmc.mbd2.integration.mekanism;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reflection-based bridge for Mekanism strict energy handlers without a hard API dependency.
 */
public final class MekanismStrictEnergyCompat {
    private static final String MODID = "mekanism";
    private static final String CAPABILITIES_CLASS = "mekanism.common.capabilities.Capabilities";
    private static final String CAP_FIELD = "STRICT_ENERGY";
    private static final String HANDLER_INTERFACE = "mekanism.api.energy.IStrictEnergyHandler";
    private static final String ACTION_CLASS = "mekanism.api.Action";
    private static final String FLOATING_LONG_CLASS = "mekanism.api.math.FloatingLong";

    private static final BigInteger BI_10K = BigInteger.valueOf(10_000);
    private static final BigInteger BI_25K = BigInteger.valueOf(25_000);
    private static final BigInteger BI_2_64_MINUS_1 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private static final BigInteger BI_LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private static final AtomicReference<Capability<?>> STRICT_CAP = new AtomicReference<>();
    private static final AtomicReference<Class<?>> HANDLER_CLASS = new AtomicReference<>();
    private static final AtomicReference<Class<?>> FLOATING_LONG = new AtomicReference<>();
    private static final AtomicReference<Object> ACTION_SIM = new AtomicReference<>();
    private static final AtomicReference<Object> ACTION_EXEC = new AtomicReference<>();
    private static final AtomicReference<Object> FL_ZERO = new AtomicReference<>();
    private static final AtomicReference<Object> FL_MAX = new AtomicReference<>();

    private MekanismStrictEnergyCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MODID);
    }

    public static Optional<Capability<?>> getCapability() {
        if (!isLoaded()) return Optional.empty();
        Capability<?> cached = STRICT_CAP.get();
        if (cached != null) return Optional.of(cached);
        try {
            Class<?> cls = Class.forName(CAPABILITIES_CLASS);
            Field f = cls.getDeclaredField(CAP_FIELD);
            Object cap = f.get(null);
            if (cap instanceof Capability<?> c) {
                STRICT_CAP.compareAndSet(null, c);
                return Optional.of(c);
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    public static Optional<Object> getHandler(BlockEntity be, Direction side) {
        Optional<Capability<?>> cap = getCapability();
        if (cap.isEmpty()) return Optional.empty();
        LazyOptional<?> opt = be.getCapability((Capability) cap.get(), side);
        return opt.resolve().map(x -> (Object) x);
    }

    public static Object createStrictHandlerProxy(Object container, StrictLongAccess access) {
        Class<?> iface = getHandlerInterface().orElseThrow();
        InvocationHandler handler = new StrictInvocationHandler(container, access);
        return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, handler);
    }

    public static Object insertRemainder(Object handler, Object joules, boolean simulate) {
        try {
            Method m = findHandlerMethod(handler, "insertEnergy");
            return m.invoke(handler, joules, simulate ? actionSimulate() : actionExecute());
        } catch (Throwable ignored) {
            return joules;
        }
    }

    public static Object extractAmount(Object handler, Object joules, boolean simulate) {
        try {
            Method m = findHandlerMethod(handler, "extractEnergy");
            return m.invoke(handler, joules, simulate ? actionSimulate() : actionExecute());
        } catch (Throwable ignored) {
            return joulesFromFe(0);
        }
    }

    public static Object actionSimulate() {
        Object cached = ACTION_SIM.get();
        if (cached != null) return cached;
        Object v = getEnumConstant(ACTION_CLASS, "SIMULATE");
        ACTION_SIM.compareAndSet(null, v);
        return v;
    }

    public static Object actionExecute() {
        Object cached = ACTION_EXEC.get();
        if (cached != null) return cached;
        Object v = getEnumConstant(ACTION_CLASS, "EXECUTE");
        ACTION_EXEC.compareAndSet(null, v);
        return v;
    }

    public static long feFloorFromJoules(Object joules) {
        try {
            Method getValue = joules.getClass().getMethod("getValue");
            Method getDecimal = joules.getClass().getMethod("getDecimal");
            long valueBits = (long) getValue.invoke(joules);
            short decimal = (short) getDecimal.invoke(joules);
            BigInteger value = new BigInteger(Long.toUnsignedString(valueBits));
            BigInteger scaled = value.multiply(BI_10K).add(BigInteger.valueOf(decimal & 0xFFFF));
            BigInteger fe = scaled.divide(BI_25K);
            if (fe.signum() <= 0) return 0L;
            if (fe.compareTo(BI_LONG_MAX) >= 0) return Long.MAX_VALUE;
            return fe.longValue();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public static Object joulesFromFe(long fe) {
        if (fe <= 0) return floatingLongZero();
        BigInteger scaled = BigInteger.valueOf(fe).multiply(BI_25K);
        BigInteger whole = scaled.divide(BI_10K);
        int dec = scaled.mod(BI_10K).intValue();
        if (whole.compareTo(BI_2_64_MINUS_1) > 0) {
            return floatingLongMax();
        }
        long bits = unsignedLongBits(whole);
        return floatingLongCreate(bits, (short) dec);
    }

    public static Object subtract(Object left, Object right) {
        try {
            Method m = left.getClass().getMethod("subtract", left.getClass());
            return m.invoke(left, right);
        } catch (Throwable ignored) {
            return left;
        }
    }

    /**
     * Access adapter used by dynamic Mekanism strict energy handler proxies.
     */
    public interface StrictLongAccess {
        long getEnergyStored(Object container);

        long getEnergyCapacity(Object container);

        void setEnergyStored(Object container, long energy);

        boolean canReceive(Object container);

        boolean canExtract(Object container);

        long receive(Object container, long amount, boolean simulate);

        long extract(Object container, long amount, boolean simulate);
    }

    private static Optional<Class<?>> getHandlerInterface() {
        if (!isLoaded()) return Optional.empty();
        Class<?> cached = HANDLER_CLASS.get();
        if (cached != null) return Optional.of(cached);
        try {
            Class<?> iface = Class.forName(HANDLER_INTERFACE);
            HANDLER_CLASS.compareAndSet(null, iface);
            return Optional.of(iface);
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    private static Method findHandlerMethod(Object handler, String name) throws ReflectiveOperationException {
        Class<?> fl = floatingLongClass();
        Class<?> act = Class.forName(ACTION_CLASS);
        return handler.getClass().getMethod(name, fl, act);
    }

    private static Class<?> floatingLongClass() {
        Class<?> cached = FLOATING_LONG.get();
        if (cached != null) return cached;
        try {
            Class<?> c = Class.forName(FLOATING_LONG_CLASS);
            FLOATING_LONG.compareAndSet(null, c);
            return c;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static Object floatingLongZero() {
        Object cached = FL_ZERO.get();
        if (cached != null) return cached;
        try {
            Field f = floatingLongClass().getDeclaredField("ZERO");
            Object v = f.get(null);
            FL_ZERO.compareAndSet(null, v);
            return v;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static Object floatingLongMax() {
        Object cached = FL_MAX.get();
        if (cached != null) return cached;
        try {
            Field f = floatingLongClass().getDeclaredField("MAX_VALUE");
            Object v = f.get(null);
            FL_MAX.compareAndSet(null, v);
            return v;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static Object floatingLongCreate(long valueBits, short decimal) {
        try {
            Method m = floatingLongClass().getMethod("create", long.class, short.class);
            return m.invoke(null, valueBits, decimal);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static long unsignedLongBits(BigInteger value) {
        if (value.signum() < 0) return 0L;
        if (value.compareTo(BI_2_64_MINUS_1) > 0) return -1L;
        if (value.compareTo(BI_LONG_MAX) <= 0) return value.longValue();
        BigInteger two64 = BigInteger.ONE.shiftLeft(64);
        return value.subtract(two64).longValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object getEnumConstant(String enumClass, String name) {
        try {
            Class<?> c = Class.forName(enumClass);
            return Enum.valueOf((Class<? extends Enum>) c, name);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static class StrictInvocationHandler implements InvocationHandler {
        private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

        private final Object container;
        private final StrictLongAccess access;

        private StrictInvocationHandler(Object container, StrictLongAccess access) {
            this.container = container;
            this.access = access;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            return switch (name) {
                case "getEnergyContainerCount" -> 1;
                case "getEnergy" ->
                        (int) args[0] == 0 ? joulesFromFe(access.getEnergyStored(container)) : floatingLongZero();
                case "getMaxEnergy" ->
                        (int) args[0] == 0 ? joulesFromFe(access.getEnergyCapacity(container)) : floatingLongZero();
                case "getNeededEnergy" -> {
                    if ((int) args[0] != 0) yield floatingLongZero();
                    long stored = access.getEnergyStored(container);
                    long cap = access.getEnergyCapacity(container);
                    yield joulesFromFe(Math.max(0L, cap - stored));
                }
                case "setEnergy" -> {
                    if ((int) args[0] != 0) yield null;
                    long fe = feFloorFromJoules(args[1]);
                    long cap = access.getEnergyCapacity(container);
                    access.setEnergyStored(container, Math.min(cap, fe));
                    yield null;
                }
                case "insertEnergy" -> {
                    if (args.length == 3) {
                        if ((int) args[0] != 0) yield args[1];
                        Object amount = args[1];
                        boolean simulate = (boolean) args[2].getClass().getMethod("simulate").invoke(args[2]);
                        if (!access.canReceive(container)) yield amount;
                        long feReq = feFloorFromJoules(amount);
                        if (feReq <= 0) yield amount;
                        long accepted = access.receive(container, feReq, simulate);
                        Object acceptedJ = joulesFromFe(accepted);
                        yield subtract(amount, acceptedJ);
                    } else {
                        Object amount = args[0];
                        boolean simulate = (boolean) args[1].getClass().getMethod("simulate").invoke(args[1]);
                        if (!access.canReceive(container)) yield amount;
                        long feReq = feFloorFromJoules(amount);
                        if (feReq <= 0) yield amount;
                        long accepted = access.receive(container, feReq, simulate);
                        Object acceptedJ = joulesFromFe(accepted);
                        yield subtract(amount, acceptedJ);
                    }
                }
                case "extractEnergy" -> {
                    if (args.length == 3) {
                        if ((int) args[0] != 0) yield floatingLongZero();
                        Object amount = args[1];
                        boolean simulate = (boolean) args[2].getClass().getMethod("simulate").invoke(args[2]);
                        if (!access.canExtract(container)) yield floatingLongZero();
                        long feReq = feFloorFromJoules(amount);
                        if (feReq <= 0) yield floatingLongZero();
                        long extracted = access.extract(container, feReq, simulate);
                        yield joulesFromFe(extracted);
                    } else {
                        Object amount = args[0];
                        boolean simulate = (boolean) args[1].getClass().getMethod("simulate").invoke(args[1]);
                        if (!access.canExtract(container)) yield floatingLongZero();
                        long feReq = feFloorFromJoules(amount);
                        if (feReq <= 0) yield floatingLongZero();
                        long extracted = access.extract(container, feReq, simulate);
                        yield joulesFromFe(extracted);
                    }
                }
                case "toString" -> "MekanismStrictEnergyProxy[" + container + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> {
                    if (method.isDefault()) {
                        yield LOOKUP.unreflectSpecial(method, method.getDeclaringClass()).bindTo(proxy).invokeWithArguments(args);
                    }
                    yield null;
                }
            };
        }
    }
}
