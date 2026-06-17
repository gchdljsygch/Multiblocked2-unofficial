package com.lowdragmc.mbd2.integration.fluxnetworks;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reflection-based bridge for Flux Networks long energy storage without a hard compile dependency.
 */
public final class FluxNetworksLongEnergyCompat {
    private static final String MODID = "fluxnetworks";
    private static final String CAPABILITIES_CLASS = "sonar.fluxnetworks.api.FluxCapabilities";
    private static final String STORAGE_INTERFACE = "sonar.fluxnetworks.api.energy.IFNEnergyStorage";
    private static final String CAP_FIELD = "FN_ENERGY_STORAGE";

    private static final AtomicReference<Capability<?>> FN_CAP = new AtomicReference<>();
    private static final AtomicReference<Class<?>> IFN_CLASS = new AtomicReference<>();
    private static final AtomicReference<Object> RECEIVE_MH = new AtomicReference<>();
    private static final AtomicReference<Object> EXTRACT_MH = new AtomicReference<>();

    private FluxNetworksLongEnergyCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MODID);
    }

    public static Optional<Capability<?>> getCapability() {
        if (!isLoaded()) return Optional.empty();
        Capability<?> cached = FN_CAP.get();
        if (cached != null) return Optional.of(cached);
        try {
            Class<?> cls = Class.forName(CAPABILITIES_CLASS);
            Field f = cls.getDeclaredField(CAP_FIELD);
            Object cap = f.get(null);
            if (cap instanceof Capability<?> c) {
                FN_CAP.compareAndSet(null, c);
                return Optional.of(c);
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    public static Optional<Class<?>> getStorageInterface() {
        if (!isLoaded()) return Optional.empty();
        Class<?> cached = IFN_CLASS.get();
        if (cached != null) return Optional.of(cached);
        try {
            Class<?> iface = Class.forName(STORAGE_INTERFACE);
            IFN_CLASS.compareAndSet(null, iface);
            return Optional.of(iface);
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    public static Optional<Object> getStorage(BlockEntity be, Direction side) {
        Optional<Capability<?>> cap = getCapability();
        if (cap.isEmpty()) return Optional.empty();
        LazyOptional<?> opt = be.getCapability((Capability) cap.get(), side);
        return opt.resolve().map(x -> (Object) x);
    }

    public static Object createStorageProxy(Object container, FluxLongAccess access) {
        Class<?> iface = getStorageInterface().orElseThrow();
        InvocationHandler handler = new StorageInvocationHandler(container, access);
        return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, handler);
    }

    public static long receive(Object storage, long amount, boolean simulate) {
        try {
            Object cached = RECEIVE_MH.get();
            if (cached == null) {
                Class<?> iface = getStorageInterface().orElse(null);
                if (iface == null) return 0L;
                var mh = MethodHandles.publicLookup().findVirtual(
                        iface,
                        "receiveEnergyL",
                        MethodType.methodType(long.class, long.class, boolean.class)
                );
                RECEIVE_MH.compareAndSet(null, mh);
                cached = mh;
            }
            var mh = (java.lang.invoke.MethodHandle) cached;
            Object out = mh.invoke(storage, amount, simulate);
            return out instanceof Long l ? l : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public static long extract(Object storage, long amount, boolean simulate) {
        try {
            Object cached = EXTRACT_MH.get();
            if (cached == null) {
                Class<?> iface = getStorageInterface().orElse(null);
                if (iface == null) return 0L;
                var mh = MethodHandles.publicLookup().findVirtual(
                        iface,
                        "extractEnergyL",
                        MethodType.methodType(long.class, long.class, boolean.class)
                );
                EXTRACT_MH.compareAndSet(null, mh);
                cached = mh;
            }
            var mh = (java.lang.invoke.MethodHandle) cached;
            Object out = mh.invoke(storage, amount, simulate);
            return out instanceof Long l ? l : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    /**
     * Access adapter used by dynamic Flux Networks storage proxies.
     */
    public interface FluxLongAccess {
        long getEnergyStored(Object container);

        long getEnergyCapacity(Object container);

        boolean canReceive(Object container);

        boolean canExtract(Object container);

        long receive(Object container, long amount, boolean simulate);

        long extract(Object container, long amount, boolean simulate);
    }

    private static class StorageInvocationHandler implements InvocationHandler {
        private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

        private final Object container;
        private final FluxLongAccess access;

        private StorageInvocationHandler(Object container, FluxLongAccess access) {
            this.container = container;
            this.access = access;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            return switch (name) {
                case "receiveEnergyL" -> access.receive(container, (long) args[0], (boolean) args[1]);
                case "extractEnergyL" -> access.extract(container, (long) args[0], (boolean) args[1]);
                case "getEnergyStoredL" -> access.getEnergyStored(container);
                case "getMaxEnergyStoredL" -> access.getEnergyCapacity(container);
                case "canReceive" -> access.canReceive(container);
                case "canExtract" -> access.canExtract(container);
                case "toString" -> "FluxLongEnergyProxy[" + container + "]";
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
