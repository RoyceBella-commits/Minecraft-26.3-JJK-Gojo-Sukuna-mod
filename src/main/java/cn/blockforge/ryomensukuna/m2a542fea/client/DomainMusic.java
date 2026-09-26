package cn.blockforge.ryomensukuna.m2a542fea.client;

import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Domain music heard by the local player; fades out once {@code inside} stops holding. */
public final class DomainMusic
extends AbstractSoundInstance
implements TickableSoundInstance {
    private static final float FADE_STEP = 0.045f;
    private static final int ACTIVATION_GRACE_TICKS = 30;
    private final Predicate<Minecraft> inside;
    private final float maxVolume;
    private int age;
    private boolean stopping;
    private boolean done;

    /** Looping track that fades in (Malevolent Shrine music). */
    public DomainMusic(SoundEvent track, Predicate<Minecraft> inside) {
        this(track, inside, SoundSource.MUSIC, true, 0.02f, 0.72f);
    }

    public DomainMusic(SoundEvent track, Predicate<Minecraft> inside, SoundSource source, boolean looping, float startVolume, float maxVolume) {
        super(track.location(), source, RandomSource.create());
        this.inside = inside;
        this.maxVolume = maxVolume;
        this.looping = looping;
        this.delay = 0;
        this.volume = startVolume;
        this.pitch = 1.0f;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    public void stopFade() {
        this.stopping = true;
    }

    /** Ticks this sound has been playing. */
    public int age() {
        return this.age;
    }

    public void tick() {
        Minecraft client = Minecraft.getInstance();
        boolean domain = client.level != null && client.player != null && this.inside.test(client);
        ++this.age;
        if (domain) {
            this.stopping = false;
        } else if (this.age > ACTIVATION_GRACE_TICKS) {
            this.stopping = true;
        }
        if (this.stopping) {
            this.volume = Math.max(0.0f, this.volume - FADE_STEP);
            if (this.volume <= 0.001f) {
                this.volume = 0.0f;
                this.done = true;
            }
        } else {
            this.volume = Math.min(this.maxVolume, this.volume + FADE_STEP);
        }
    }

    public boolean isStopped() {
        return this.done;
    }
}
