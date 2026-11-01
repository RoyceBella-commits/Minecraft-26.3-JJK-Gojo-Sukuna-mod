package cn.blockforge.ryomensukuna.client;

import cn.blockforge.ryomensukuna.m2a542fea.client.InputGate;
import cn.blockforge.ryomensukuna.m2a542fea.client.ChargeSession;
import cn.blockforge.ryomensukuna.m2a542fea.client.HudPreferences;
import cn.blockforge.ryomensukuna.m2a542fea.combat.DuelRules;
import cn.blockforge.ryomensukuna.m2a542fea.entity.npc.NpcTactics;
import com.google.gson.JsonParser;
import cn.blockforge.ryomensukuna.m2a542fea.progression.StageRules;
import cn.blockforge.ryomensukuna.m2a542fea.skill.CurseState;
import cn.blockforge.ryomensukuna.m2a542fea.skill.Skill;
import cn.blockforge.ryomensukuna.m2a542fea.skill.mahoraga.Adaptation;
import cn.blockforge.ryomensukuna.m2a542fea.gojo.InfinityBreach;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;

/** Headless checks of the actual input state machine and preference file, not game rendering. */
public final class ClientControlsCheck {
    private static int checks;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        InputGate gate = new InputGate();
        ChargeSession session = new ChargeSession();
        for (int i=0;i<5;i++) check(!gate.update(false,true,true),"disabled clicks drained");
        check(!gate.update(true,true,true),"unlock while held does not trigger");
        check(!gate.held(),"unlock hold excluded from charge session");
        check(session.tick(gate.held(),false,true,0,0)==null && !session.isCharging(),"no deferred charge");
        check(!gate.update(true,false,false),"release arms gate");
        boolean edge=gate.update(true,true,true);
        check(edge,"fresh press after unlock accepted");
        session.tick(gate.held(),edge,true,0,1_000_000_000L);
        check(session.isCharging(),"fresh press starts charge");
        for(int i=0;i<5;i++) check(!gate.update(true,true,true),"holding/repeat never toggles again");
        check(!gate.update(true,false,false),"release is not a press");
        var cast=session.tick(gate.held(),false,true,0,2_000_000_000L);
        check(cast!=null && cast.seconds()==1.0f,"release emits one charged cast");
        check(session.tick(false,false,true,0,3_000_000_000L)==null,"no duplicate release");
        check(gate.update(true,false,true),"quick click accepted");
        check(session.tick(false,true,true,0,4_000_000_000L)!=null,"quick click casts");
        gate.update(true,true,true);session.tick(true,true,true,0,5_000_000_000L);
        check(!gate.update(false,true,true),"menu/deactivation disables press");
        check(session.tick(gate.held(),false,false,0,6_000_000_000L)==null && !session.isCharging(),"menu cancels without cast");
        check(!gate.update(true,true,true) && !gate.held(),"menu close while held stays suppressed");
        gate.update(true,false,false);
        check(gate.update(true,true,true),"press after menu release works");
        gate.reset();
        check(!gate.update(true,true,true),"world reconnect requires release");

        Path directory=Path.of(args[0]);Files.createDirectories(directory);
        Path file=Files.createTempDirectory(directory,"prefs-").resolve("sukuna-client.json");
        HudPreferences preferences=new HudPreferences(file);
        check(preferences.visible() && Files.exists(file),"missing config defaults visible and is created");
        preferences.toggle();check(!preferences.visible(),"toggle off");
        check(!new HudPreferences(file).visible(),"hidden preference survives restart");
        check(!new HudPreferences(file).visible(),"another world does not change preference");
        new HudPreferences(file).toggle();check(new HudPreferences(file).visible(),"toggle back persisted");
        Files.writeString(file,"{\"hudVisible\":false,\"futureSetting\":7}");
        new HudPreferences(file).toggle();
        check(JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("futureSetting").getAsInt()==7,"unrelated config retained");
        Files.writeString(file,"{\"hudVisible\":\"false\"}");
        check(new HudPreferences(file).visible(),"wrong field type uses true default");
        Files.writeString(file,"{}");check(new HudPreferences(file).visible(),"missing field defaults true");
        Files.writeString(file,"invalid json");check(new HudPreferences(file).visible(),"malformed config does not crash");
        check(!new HudPreferences(file).lowFx(),"accessibility flags default off");
        Files.writeString(file,"{\"hudVisible\":true,\"lowFx\":true,\"reduceShake\":true}");
        HudPreferences access=new HudPreferences(file);
        check(access.lowFx() && access.reduceShake() && !access.noFlash(),"accessibility flags read from config");
        progression();
        growthAndBalance();
        npcTactics();
        duelRules();
        System.out.println("PASS: "+checks+" client input, preference, progression, 2.1/2.2 balance-rule and NPC tactics checks (no in-game validation)");
    }

    /** 2.1 rules: per-stage fist damage and Black Flash, domain duration, volume scaling, Mahoraga adaptation pace. */
    private static void growthAndBalance() {
        for (int st = 1; st < StageRules.MAX_STAGE; st++) {
            check(StageRules.unarmedDamage(st + 1) > StageRules.unarmedDamage(st), "fist damage rises every stage (" + st + ")");
            check(StageRules.blackFlashChance(st + 1) > StageRules.blackFlashChance(st), "Black Flash chance rises every stage (" + st + ")");
            check(StageRules.goldHp(st + 1) > StageRules.goldHp(st) && StageRules.armor(st + 1) > StageRules.armor(st), "gold and armor rise every stage (" + st + ")");
        }
        check(StageRules.unarmedDamage(StageRules.MAX_STAGE) == 20f, "full-strength fist deals 20");
        check(Math.abs(StageRules.blackFlashChance(StageRules.MAX_STAGE) - 0.2f) < 1e-6f && StageRules.blackFlashChance(99) <= 0.2f, "Black Flash capped at 20%");
        check(StageRules.blackFlashChance(0) == 0f && StageRules.BLACK_FLASH_MULTIPLIER == 3f, "no Black Flash before awakening; x3 damage");
        check(StageRules.domainTicks(4) == 400 && StageRules.domainTicks(5) == 600, "domains last 20 s at IV and 30 s at V");
        check(Math.abs(Math.pow(StageRules.VOLUME_X3, 3) - 3.0) < 1e-9 && Math.abs(Math.pow(StageRules.VOLUME_X2, 3) - 2.0) < 1e-9, "range scaling is by volume");
        check(StageRules.DOMAIN_RADIUS == 64.0, "both domains reach 64 blocks (the Malevolent Shrine radius)");
        double purple = 4.5 * StageRules.VOLUME_X3 * StageRules.VOLUME_X3;
        check(purple > 9.3 && purple < 9.4, "Hollow Purple radius ~9.4 (x3 volume twice)");
        Adaptation.Entry melee = new Adaptation.Entry("minecraft:player_attack", false, Adaptation.Evolution.NONE, 0);
        check(!melee.advance(Adaptation.STAGE_TICKS - 1) && melee.stage() == 0, "no adaptation before 3 s");
        check(melee.advance(1) && melee.stage() == 1 && Math.abs(melee.multiplier() - 0.8f) < 1e-6f, "first stage after 3 s: damage x0.8");
        melee.advance(Adaptation.STAGE_TICKS * 4);
        check(melee.immune() && melee.multiplier() == 0f, "fully adapted after 15 s");
        check(Adaptation.STAGE_TICKS == 60, "one adaptation stage every 60 ticks");
        damageTaken();
        infinityBreach();
    }

    /** 2.2: share of damage taken falls each stage to 1% (10% between Gojo / Sukuna sorcerers). */
    private static void damageTaken() {
        check(StageRules.takenFraction(0, false) == 1f && StageRules.takenFraction(0, true) == 1f, "unawakened takes full damage");
        for (int st = 0; st < StageRules.MAX_STAGE; st++) {
            check(StageRules.takenFraction(st + 1, false) < StageRules.takenFraction(st, false)
                && StageRules.takenFraction(st + 1, true) < StageRules.takenFraction(st, true), "damage taken falls every stage (" + st + ")");
        }
        check(Math.abs(StageRules.takenFraction(5, false) - 0.01f) < 1e-6f, "stage V takes 1% of normal damage");
        check(Math.abs(StageRules.takenFraction(5, true) - 0.10f) < 1e-6f, "stage V takes 10% from sorcerers");
        check(StageRules.takenFraction(3, true) > StageRules.takenFraction(3, false), "sorcerers always hurt more than the rest");
    }

    /** 2.2: three hits of one kind (<= 10 s apart) break that kind of Infinity for 5 s; repeatable. */
    private static void infinityBreach() {
        InfinityBreach b = new InfinityBreach();
        InfinityBreach.Category slash = InfinityBreach.Category.SLASH;
        check(b.hit(slash, 0) == InfinityBreach.Result.COUNTED && b.hit(slash, 20) == InfinityBreach.Result.COUNTED, "first two slashes are stopped");
        check(b.hit(InfinityBreach.Category.FLAME, 25) == InfinityBreach.Result.COUNTED && b.count(slash) == 2, "kinds are counted separately");
        check(b.hit(slash, 40) == InfinityBreach.Result.BROKE && b.open(slash, 41), "third slash breaks through");
        check(b.hit(slash, 139) == InfinityBreach.Result.OPEN && !b.open(slash, 140), "open for exactly 5 s");
        check(b.hit(slash, 141) == InfinityBreach.Result.COUNTED && b.count(slash) == 1, "after the window the count starts again");
        check(b.hit(slash, 400) == InfinityBreach.Result.COUNTED && b.count(slash) == 1, "a 10 s pause resets the count");
        check(!b.open(InfinityBreach.Category.FIST, 40), "other kinds stay closed");
    }

    /** 2.2.11 NPC against NPC: no blow decides the fight, healing is slow, and the AI picks and leads its techniques. */
    private static void duelRules() {
        float max = 200.0f;
        check(DuelRules.capped(1000.0f, max) == max * DuelRules.HIT_CAP && DuelRules.capped(20.0f, max) == 20.0f, "one blow from an NPC takes at most 35%");
        check(Math.abs(DuelRules.finisherShare(false) - 0.30f) < 1e-6f && Math.abs(DuelRules.finisherShare(true) - 0.15f) < 1e-6f, "World Cut / Purple 30% between NPCs, the fused Purple 15%");
        check(DuelRules.finisherShare(false) <= DuelRules.HIT_CAP, "the finisher fits under the cap");
        check(Math.ceil(1.0 / DuelRules.finisherShare(false)) >= 4, "it takes at least four finishers alone to fell an NPC");
        check(DuelRules.FINISHER_COOLDOWN < 1200 && DuelRules.FINISHER_COOLDOWN >= 600, "finishers come back every 30-60 s");
        check(DuelRules.regen(false) == 0.5f && DuelRules.regen(true) == 2.5f, "0.5 health a second in a fight, 2.5 at rest");
        check(DuelRules.RCT_HEAL == 50.0f && DuelRules.RCT_CHANNEL == 30 && DuelRules.RCT_COOLDOWN == 600, "reverse cursed technique: 1.5 s channel, 50 health, every 30 s");
        check(DuelRules.RCT_HEAL / (DuelRules.RCT_COOLDOWN / 20.0f) < 2.0f, "the heal is less than 2 health a second over its cooldown");
        check(DuelRules.recovery(30, 60, 0f) == 30 && DuelRules.recovery(30, 60, 0.999f) == 60, "recovery spans its range");
        check(DuelRules.LIGHT_MAX <= DuelRules.MEDIUM_MIN, "light techniques recover sooner than medium ones");
        check(DuelRules.repeatWeight(1.0f, 1) == 0.5f && DuelRules.repeatWeight(1.0f, 2) == 0.25f && DuelRules.repeatWeight(1.0f, 0) == 1.0f, "a repeated technique counts half each time");
        check(DuelRules.pick(0.0f, 0f, 1f, 1f) == 1 && DuelRules.pick(0.99f, 0f, 1f, 1f) == 2 && DuelRules.pick(0.5f, 0f, 0f) == -1, "weighted pick skips zero weights");
        int[] seen = new int[3];
        for (int i = 0; i < 1000; i++) seen[DuelRules.pick(i / 1000.0f, 1f, 2f, 1f)]++;
        check(Math.abs(seen[1] - 500) <= 2 && Math.abs(seen[0] - 250) <= 2, "weights are honoured");
        double[] lead = DuelRules.lead(20.0, 2.0, 0.3, 0.0, 0.0, 6.0);
        check(Math.abs(lead[0] - 3.0) < 1e-9 && lead[2] == 0.0, "a shot leads a running foe by its run over the flight");
        lead = DuelRules.lead(60.0, 2.0, 1.0, 0.0, 0.0, 6.0);
        check(Math.abs(lead[0] - 6.0) < 1e-9, "the lead is capped at 6 blocks");
        check(DuelRules.COUNTER_CHANCE < 1.0f, "a finisher is not always answered, so no endless exchange");
        check(DuelRules.vsFate(false) > DuelRules.vsFate(true) && DuelRules.vsFate(true) >= 1.0f, "Sukuna, with no Infinity, hits the Fate spirits harder than Gojo");
    }

    /** 2.2.10 NPC fight rules: a chaser is fought back, a stuck sorcerer uses its mobility, leaps land where aimed. */
    private static void npcTactics() {
        NpcTactics t = new NpcTactics();
        NpcTactics.Reaction r = NpcTactics.Reaction.NONE;
        for (int i = 0; i < NpcTactics.REACT_TICKS - 1; i++) r = t.pressed(true, false, i);
        check(r == NpcTactics.Reaction.NONE, "a foe that has just closed in is not fought yet");
        for (int i = NpcTactics.REACT_TICKS - 1; i < NpcTactics.CLOSE_LIMIT - 1; i++) r = t.pressed(true, false, i);
        check(r == NpcTactics.Reaction.NONE, "without mobility it backs off for up to 1.5 s");
        check(t.pressed(true, false, NpcTactics.CLOSE_LIMIT - 1) == NpcTactics.Reaction.COUNTER, "kept on it for 1.5 s: fights back hand to hand");
        NpcTactics u = new NpcTactics();
        for (int i = 0; i < NpcTactics.REACT_TICKS - 1; i++) u.pressed(true, true, 100 + i);
        check(u.pressed(true, true, 105) == NpcTactics.Reaction.BREAK_AWAY, "first close-in: breaks away with its mobility");
        u.pressed(false, true, 106);
        for (int i = 0; i < NpcTactics.REACT_TICKS - 1; i++) u.pressed(true, true, 150 + i);
        check(u.pressed(true, true, 155) == NpcTactics.Reaction.COUNTER, "closing in again within 10 s is a chase: fought at once");
        NpcTactics w = new NpcTactics();
        w.pressed(true, true, 0);
        w.pressed(false, true, 1);
        for (int i = 0; i < NpcTactics.REACT_TICKS - 1; i++) w.pressed(true, true, 400 + i);
        check(w.pressed(true, true, 405) == NpcTactics.Reaction.BREAK_AWAY, "a close-in over 10 s after the last is a fresh one");
        NpcTactics c = new NpcTactics();
        for (int i = 0; i < 20; i++) c.pressed(true, false, i);
        c.calm();
        check(c.pressed(true, false, 20) == NpcTactics.Reaction.NONE, "a hand-to-hand window ends the close-in streak");
        for (int i = 21; i < 25; i++) c.pressed(true, false, i);
        check(c.pressed(true, false, 25) == NpcTactics.Reaction.COUNTER, "but the earlier close-in still makes the next one a chase");

        NpcTactics s = new NpcTactics();
        check(!s.stuck(true, 20.0, 0.0, true), "first check only marks the distance");
        check(!s.stuck(true, 20.0, 0.2, true) && !s.stuck(true, 20.0, 0.2, true), "cut off for 1 s: still trying on foot");
        check(s.stuck(true, 20.0, 0.2, true), "cut off for 1.5 s without progress: uses its mobility");
        check(!s.stuck(true, 20.0, 0.2, true), "the count starts again after a try");
        check(!s.stuck(true, 18.5, 0.2, true), "getting closer is progress");
        s.stuck(true, 18.5, 0.2, true);
        s.stuck(true, 18.5, 0.2, true);
        check(!s.stuck(true, 18.5, 3.0, true), "moving about is progress");
        s.stuck(true, 18.5, 0.2, true);
        s.stuck(true, 18.5, 0.2, true);
        check(!s.stuck(true, 18.5, 0.2, false) && s.stuck(true, 18.5, 0.2, true), "waits for its mobility, then goes at once");
        check(!s.stuck(false, 18.5, 0.0, true), "a foe in reach or in sight never counts as stuck");

        double[] flat = fly(NpcTactics.leap(6.0, 0.0, 8.0, 2.0), 0.0);
        check(flat[0] >= 2.0 && Math.abs(flat[1] - 10.0) < 0.5, "a bound over flat ground lands 10 blocks out, 2+ blocks high");
        double[] pit = fly(NpcTactics.leap(3.0, 6.0, 3.0, 1.5), 6.0);
        check(pit[0] >= 7.5 && Math.abs(pit[1] - Math.sqrt(18.0)) < 0.5, "out of a 6-deep pit: clears the rim and lands by the foe");
        double[] side = fly(NpcTactics.leap(10.0, 0.0, 0.0, 0.6), 0.0);
        check(side[0] < 1.5 && Math.abs(side[1] - 10.0) < 0.5, "a sidestep is a low, quick bound of 10 blocks");
        double[] up = NpcTactics.leap(0.0, 4.0, 0.0, 1.0);
        check(up[0] == 0.0 && up[2] == 0.0 && NpcTactics.apex(up[1]) >= 5.0, "straight up when the foe is overhead");
        double[] far = NpcTactics.leap(60.0, 0.0, 0.0, 2.0);
        check(far[0] <= NpcTactics.MAX_FLAT && fly(far, 0.0)[1] < 60.0, "launch speed is capped: very far points fall short");
    }

    /** A leap flown as a mob flies it (the launch tick still on the ground): {highest point, flat distance back down at dy}. */
    private static double[] fly(double[] v, double dy) {
        double x = 0.0, y = 0.0, z = 0.0, vx = v[0], vy = v[1], vz = v[2], top = 0.0;
        for (int t = 0; t < 400; t++) {
            x += vx;
            y += vy;
            z += vz;
            top = Math.max(top, y);
            double f = t == 0 ? NpcTactics.GROUND_FRICTION : NpcTactics.FRICTION;
            vx *= f;
            vz *= f;
            vy = (vy - NpcTactics.GRAVITY) * NpcTactics.DRAG_Y;
            if (vy < 0.0 && y <= dy) break;
        }
        return new double[]{top, Math.sqrt(x * x + z * z)};
    }

    /** Pure progression rules: route lock, item consumption, legacy migration, practice gates, gold. */
    private static void progression() {
        check(StageRules.evaluateUse(StageRules.NONE,StageRules.GOJO,0,false)==StageRules.UseResult.AWAKENED,"first token use awakens Gojo");
        check(StageRules.evaluateUse(StageRules.NONE,StageRules.SUKUNA,0,false)==StageRules.UseResult.AWAKENED,"first finger use awakens Sukuna");
        check(StageRules.evaluateUse(StageRules.GOJO,StageRules.SUKUNA,1,true)==StageRules.UseResult.WRONG_ROUTE,"other route item refused");
        check(StageRules.evaluateUse(StageRules.SUKUNA,StageRules.GOJO,3,true)==StageRules.UseResult.WRONG_ROUTE,"route lock persists at any stage");
        check(StageRules.evaluateUse(StageRules.GOJO,StageRules.GOJO,1,false)==StageRules.UseResult.NOT_READY,"practice incomplete not consumed");
        check(StageRules.evaluateUse(StageRules.GOJO,StageRules.GOJO,5,true)==StageRules.UseResult.MAXED,"max stage not consumed");
        check(StageRules.evaluateUse(StageRules.GOJO,StageRules.GOJO,2,true)==StageRules.UseResult.ADVANCED,"practice done advances");
        check(!StageRules.practiceDone(1,4,0,false,false,0) && StageRules.practiceDone(1,5,0,false,false,0),"stage I needs 5 hits");
        check(!StageRules.practiceDone(2,15,1,false,false,0) && StageRules.practiceDone(2,15,2,false,false,0),"stage II needs two abilities");
        check(!StageRules.practiceDone(3,30,3,false,false,0) && StageRules.practiceDone(3,25,2,true,false,0),"stage III needs a heavy attack");
        check(!StageRules.practiceDone(4,99,5,true,true,4) && StageRules.practiceDone(4,0,0,false,true,5),"stage IV needs domain then 5 hits");
        check(StageRules.legacyStage(0)==0 && StageRules.legacyStage(1)==1 && StageRules.legacyStage(3)==2 && StageRules.legacyStage(10)==3 && StageRules.legacyStage(20)==4,"legacy finger mapping");
        check(StageRules.goldHp(1)==20f && StageRules.goldHp(5)==60f && StageRules.armor(1)==10f && StageRules.armor(5)==20f,"stage totals");
        check(StageRules.goldAfterUpgrade(0f,0,1)==20f,"awakening fills gold");
        check(StageRules.goldAfterUpgrade(5f,1,2)==15f,"upgrade adds only new capacity");
        check(StageRules.goldAfterUpgrade(20f,1,2)==30f,"full gold stays full");
        float[] split=StageRules.splitDamage(6f,10f);
        check(split[0]==6f && split[1]==4f,"gold absorbs before red");
        check(StageRules.cappedArmor(26f)==20f,"armor capped at 20");
        CurseState legacy=CurseState.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString("{\"energy\":5,\"fingers\":3,\"selected\":1}")).getOrThrow();
        check(legacy.route()==StageRules.SUKUNA && legacy.stage()==2 && legacy.goldHp()==30f,"legacy save migrates to Sukuna stage II with full gold");
        check(legacy.unlocked(Skill.KAI) && legacy.unlocked(Skill.CLEAVE) && legacy.unlocked(Skill.CURSED_BARRAGE),"legacy unlocks kept");
        CurseState full=CurseState.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString("{\"fingers\":20}")).getOrThrow();
        check(full.stage()==4 && full.unlocked(Skill.DOMAIN) && full.unlocked(Skill.MAHORAGA) && !full.infiniteEnergy(),"20 fingers = domain stage, not yet infinite");
        CurseState fresh=CurseState.fresh();
        check(!fresh.awakened() && fresh.unlockedMask()==0,"holding nothing unlocks nothing");
        CurseState gojo=fresh.withProgress(StageRules.GOJO,1,20f);
        check(gojo.unlocked(Skill.AO) && !gojo.unlocked(Skill.KAI) && !gojo.unlocked(Skill.AKA),"route skills only");
        CurseState afterDomain=gojo.withProgress(StageRules.GOJO,4,50f).withHit().withFlag(CurseState.FLAG_DOMAIN,true);
        check(afterDomain.postDomainHits()==0,"hits before the domain do not count for stage V");
        for(int i=0;i<5;i++) afterDomain=afterDomain.withHit();
        check(afterDomain.practiceDone(),"five hits after the domain complete stage IV practice");
        CurseState round=CurseState.CODEC.parse(JsonOps.INSTANCE,CurseState.CODEC.encodeStart(JsonOps.INSTANCE,afterDomain).getOrThrow()).getOrThrow();
        check(round.equals(afterDomain),"state survives save/load");
        check(gojo.withProgress(StageRules.GOJO,5,60f).infiniteEnergy(),"stage V infinite energy");
    }
}
