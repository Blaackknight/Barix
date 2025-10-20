package fr.bck.barix.example;

import fr.bck.barix.api.IAntiXrayCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ExampleAntiXrayCompat
 *
 * This example provider demonstrates how to:
 * - veto Barix Anti-Xray for certain situations (shouldHide false)
 * - add extra target blocks to be masked
 *
 * Registration snippet (e.g., during your mod setup):
 *   fr.bck.barix.api.BarixAPI.registerAntiXrayCompat(new fr.bck.barix.example.ExampleAntiXrayCompat());
 */
public final class ExampleAntiXrayCompat implements IAntiXrayCompat {
    @Override
    public String id() { return "example-compat"; }

    @Override
    public boolean shouldHide(ServerPlayer sp, BlockPos pos, BlockState state, Level lvl) {
        // Example rule: don't hide emerald ore above Y=50 (for testing/demo)
        if (state.is(Blocks.EMERALD_ORE) && pos.getY() > 50) return false;
        return true; // default allow
    }

    @Override
    public boolean isTargetBlock(BlockState state) {
        // Example: also mask raw copper blocks (purely as a demo)
        return state.is(Blocks.RAW_COPPER_BLOCK);
    }
}

