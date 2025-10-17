package fr.bck.barix.tags;

import fr.bck.barix.BarixConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class BarixBlockTags {
    private BarixBlockTags() {}

    // Tag regroupant les minerais à masquer par l’anti-xray
    public static final TagKey<Block> ANTIXRAY_ORES = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(BarixConstants.MODID, "anti_xray_ores")
    );
}

