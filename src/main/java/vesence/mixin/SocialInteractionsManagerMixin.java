package vesence.mixin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Фикс ванильного фриза при входящих чат-сообщениях (27–1058 мс каждые ~5 мин).
 *
 * Цепочка фриза:
 *   ChatMessageS2CPacket → isPlayerMuted → isPlayerBlocked → fetchBlockList →
 *   forceFetchBlockList → HttpURLConnection (блокирующий HTTPS к Mojang API)
 *
 * Решение: перехватываем isPlayerMuted в HEAD.
 * - Если вызов из нашего фонового потока — не трогаем (ThreadLocal-флаг).
 * - Иначе: возвращаем кешированный результат или false (оптимистично),
 *   и запускаем настоящую проверку в daemon-потоке.
 */
@Environment(EnvType.CLIENT)
@Mixin(net.minecraft.client.gui.screens.social.PlayerSocialManager.class)
public abstract class SocialInteractionsManagerMixin {

    /** Результат проверки UUID: true = muted, false = ok. */
    private static final ConcurrentHashMap<UUID, Boolean> CACHE = new ConcurrentHashMap<>(64);
    /** UUID с уже запущенной фоновой проверкой. */
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private static final long TTL_MS = 5L * 60L * 1000L;
    private static volatile long cacheTs = 0L;

    /**
     * Маркер: «этот поток — наш фоновый проверяльщик».
     * Когда он выставлен — инжект пропускает перехват и даёт пройти
     * оригинальному коду (чтобы не было бесконечной рекурсии).
     */
    private static final ThreadLocal<Boolean> IS_ASYNC = new ThreadLocal<>();

    @Inject(method = "shouldHideMessageFrom", at = @At("HEAD"), cancellable = true,
            require = 0, expect = 0)
    private void vesence$asyncIsPlayerMuted(UUID uuid, CallbackInfoReturnable<Boolean> cir) {
        // Если мы уже в нашем фоновом потоке — не перехватываем, пусть идёт оригинал.
        if (Boolean.TRUE.equals(IS_ASYNC.get())) return;

        if (uuid == null) { cir.setReturnValue(false); return; }

        // TTL-инвалидация кеша.
        long now = System.currentTimeMillis();
        if (now - cacheTs > TTL_MS) { CACHE.clear(); IN_FLIGHT.clear(); cacheTs = now; }

        Boolean cached = CACHE.get(uuid);
        if (cached != null) { cir.setReturnValue(cached); return; }

        // Запускаем фоновую проверку (только один раз на UUID).
        if (IN_FLIGHT.add(uuid)) {
            final PlayerSocialManager self = (net.minecraft.client.gui.screens.social.PlayerSocialManager) (Object) this;
            Thread t = new Thread(() -> {
                IS_ASYNC.set(Boolean.TRUE);
                try {
                    boolean muted = self.shouldHideMessageFrom(uuid);
                    CACHE.put(uuid, muted);
                    cacheTs = System.currentTimeMillis();
                } catch (Throwable ignored) {
                    CACHE.put(uuid, false);
                } finally {
                    IN_FLIGHT.remove(uuid);
                    IS_ASYNC.remove();
                }
            }, "vesence-mute-check");
            t.setDaemon(true);
            t.start();
        }

        // Оптимистичный ответ пока фон не ответил.
        cir.setReturnValue(false);
    }
}
