package com.raidtracker.ui;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;

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
}