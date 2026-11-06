package com.raidtracker.ui;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;

import java.util.EnumSet;

@AllArgsConstructor
public enum RaidUniques {
    DEX("Dexterous Prayer Scroll", ItemID.RAIDS_PRAYERSCROLL),
    ARCANE("Arcane Prayer Scroll", ItemID.RAIDS_PRAYERSCROLL_AUGURY),
    TWISTED_BUCKLER("Twisted Buckler", ItemID.TWISTED_BUCKLER),
    DHCB("Dragon Hunter Crossbow", ItemID.DRAGONHUNTER_XBOW),
    DINNY_B("Dinh's Bulwark", ItemID.DINHS_BULWARK),
    ANCESTRAL_HAT("Ancestral Hat", ItemID.ANCESTRAL_HAT),
    ANCESTRAL_TOP("Ancestral Robe Top", ItemID.ANCESTRAL_ROBE_TOP),
    ANCESTRAL_BOTTOM("Ancestral Robe Bottom", ItemID.ANCESTRAL_ROBE_BOTTOM),
    DRAGON_CLAWS("Dragon Claws", ItemID.DRAGON_CLAWS),
    ELDER_MAUL("Elder Maul", ItemID.ELDER_MAUL),
    KODAI("Kodai Insignia", ItemID.KODAI_INSIGNIA),
    TWISTED_BOW("Twisted Bow", ItemID.TWISTED_BOW),
    DUST("Metamorphic Dust", ItemID.RAIDS_CHALLENGE_MORPH),
    TWISTED_KIT("Twisted Kit", ItemID.ANCESTRAL_ROBES_TWISTED_KIT),
    OLMLET("Olmlet", ItemID.OLMPET),

    AVERNIC("Avernic defender hilt", ItemID.INFERNAL_DEFENDER_HILT),
    RAPIER("Ghrazi rapier", ItemID.GHRAZI_RAPIER),
    SANGSTAFF("Sanguinesti staff (uncharged)", ItemID.SANGUINESTI_STAFF_UNCHARGED),
    JUSTI_FACEGUARD("Justiciar faceguard", ItemID.JUSTICIAR_FACEGUARD),
    JUSTI_CHESTGUARD("Justiciar chestguard", ItemID.JUSTICIAR_CHESTGUARD),
    JUSTI_LEGGUARDS("Justiciar legguards", ItemID.JUSTICIAR_LEG_GUARDS),
    SCYTHE("Scythe of vitur (uncharged)", ItemID.SCYTHE_OF_VITUR_UNCHARGED),
    LILZIK("Lil' Zik", ItemID.VERZIKPET),

    ARCHIVISTS_FOLIO("Archivist's folio (inert)", ItemID.ARCHIVISTS_FOLIO_EMPTY),

    HORN_OF_BRAN("Horn of Bran", ItemID.TFA_ZEAL_REWARD),
    ZORYAS_TOME("Zorya's tome (uncharged)", ItemID.ZORYAS_TOME_UNCHARGED),
    RONDACHE("Rondache", ItemID.RONDACHE),
    ASCENSION_CROSSBOWS("Ascension crossbows", ItemID.ASCENSION_CROSSBOWS),
    OBLIGATOR("Obligator (uncharged)", ItemID.OBLIGATOR_UNCHARGED),
    ORION("Orion", ItemID.OCELLUSPET),

    OSMUMTENS_FANG("Osmumten's fang", ItemID.OSMUMTENS_FANG),
    LIGHTBEARER("Lightbearer", ItemID.LIGHTBEARER),
    ELIDINIS_WARD("Elidinis' ward", ItemID.ELIDINIS_WARD),
    MASORI_MASK("Masori mask", ItemID.MASORI_MASK),
    MASORI_BODY("Masori body", ItemID.MASORI_BODY),
    MASORI_CHAPS("Masori chaps", ItemID.MASORI_CHAPS),
    TUMEKENS_SHADOW("Tumeken's shadow (uncharged)", ItemID.TUMEKENS_SHADOW),
    TUMEKENS_GUARDIAN("Tumeken's guardian", ItemID.WARDENPET_TUMEKEN);

    @Getter
    private final String name;

    @Getter
    private final int itemID;

    public static final EnumSet<RaidUniques> COX_UNIQUES = EnumSet.of(
        DEX,
        ARCANE,
        TWISTED_BUCKLER,
        DHCB,
        DINNY_B,
        ANCESTRAL_HAT,
        ANCESTRAL_TOP,
        ANCESTRAL_BOTTOM,
        DRAGON_CLAWS,
        ELDER_MAUL,
        KODAI,
        TWISTED_BOW,
        DUST,
        TWISTED_KIT,
        OLMLET
    );

    public static final EnumSet<RaidUniques> TOA_UNIQUES = EnumSet.of(
        OSMUMTENS_FANG,
        LIGHTBEARER,
        ELIDINIS_WARD,
        MASORI_MASK,
        MASORI_BODY,
        MASORI_CHAPS,
        TUMEKENS_SHADOW,
        TUMEKENS_GUARDIAN
    );

    public static final EnumSet<RaidUniques> TFA_UNIQUES = EnumSet.of(
        HORN_OF_BRAN,
        ZORYAS_TOME,
        RONDACHE,
        ASCENSION_CROSSBOWS,
        OBLIGATOR,
        ORION
    );

    /**
     * Chest id, or a confirmed special-loot name, for a Fractured Archive unique.
     * The folio is regular loot. Orion is included by id and by the broadcast name.
     */
    public static RaidUniques archiveUnique(int id, String name) {
        if (id == ItemID.TFA_ZEAL_REWARD || sameName(name, HORN_OF_BRAN)) {
            return HORN_OF_BRAN;
        }
        if (id == ItemID.ZORYAS_TOME || id == ItemID.ZORYAS_TOME_UNCHARGED || sameName(name, ZORYAS_TOME)) {
            return ZORYAS_TOME;
        }
        if (id == ItemID.RONDACHE || sameName(name, RONDACHE)) {
            return RONDACHE;
        }
        if (id == ItemID.ASCENSION_CROSSBOWS || sameName(name, ASCENSION_CROSSBOWS)) {
            return ASCENSION_CROSSBOWS;
        }
        if (id == ItemID.OBLIGATOR || id == ItemID.OBLIGATOR_UNCHARGED || sameName(name, OBLIGATOR)) {
            return OBLIGATOR;
        }
        if (id == ItemID.OCELLUSPET || id == ItemID.OCELLUSPET_VARIANT_1 || sameName(name, ORION)) {
            return ORION;
        }
        return null;
    }

    public static boolean isArchiveUniqueId(int id) {
        return archiveUnique(id, null) != null;
    }

    private static boolean sameName(String name, RaidUniques unique) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim().replace('\u2019', '\'');
        if (unique.getName().equalsIgnoreCase(trimmed)) {
            return true;
        }
        if (unique == OBLIGATOR && trimmed.equalsIgnoreCase("Obligator")) {
            return true;
        }
        return unique == ZORYAS_TOME && trimmed.equalsIgnoreCase("Zorya's tome");
    }

    public static final EnumSet<RaidUniques> TOB_UNIQUES = EnumSet.of(
        AVERNIC,
        RAPIER,
        SANGSTAFF,
        JUSTI_FACEGUARD,
        JUSTI_CHESTGUARD,
        JUSTI_LEGGUARDS,
        SCYTHE,
        LILZIK
    );
}