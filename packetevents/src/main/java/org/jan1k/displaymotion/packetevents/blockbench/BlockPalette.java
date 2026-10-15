package org.jan1k.displaymotion.packetevents.blockbench;

import org.bukkit.Material;

/** Picks the vanilla block whose average color is closest to a color. */
public final class BlockPalette {

    // average colors of the block textures, eyeballed from the vanilla pack. good enough to pick a block
    private static final Object[][] BLOCKS = {
            {Material.WHITE_CONCRETE, 207, 213, 214}, {Material.ORANGE_CONCRETE, 224, 97, 1},
            {Material.MAGENTA_CONCRETE, 169, 48, 159}, {Material.LIGHT_BLUE_CONCRETE, 36, 137, 199},
            {Material.YELLOW_CONCRETE, 241, 175, 21}, {Material.LIME_CONCRETE, 94, 169, 24},
            {Material.PINK_CONCRETE, 214, 101, 143}, {Material.GRAY_CONCRETE, 55, 58, 62},
            {Material.LIGHT_GRAY_CONCRETE, 125, 125, 115}, {Material.CYAN_CONCRETE, 21, 119, 136},
            {Material.PURPLE_CONCRETE, 100, 32, 156}, {Material.BLUE_CONCRETE, 45, 47, 143},
            {Material.BROWN_CONCRETE, 96, 60, 32}, {Material.GREEN_CONCRETE, 73, 91, 36},
            {Material.RED_CONCRETE, 142, 33, 33}, {Material.BLACK_CONCRETE, 8, 10, 15},
            {Material.WHITE_WOOL, 234, 236, 237}, {Material.ORANGE_WOOL, 241, 118, 20},
            {Material.MAGENTA_WOOL, 190, 69, 180}, {Material.LIGHT_BLUE_WOOL, 58, 175, 217},
            {Material.YELLOW_WOOL, 249, 198, 40}, {Material.LIME_WOOL, 112, 185, 26},
            {Material.PINK_WOOL, 238, 141, 172}, {Material.GRAY_WOOL, 63, 68, 72},
            {Material.LIGHT_GRAY_WOOL, 142, 142, 135}, {Material.CYAN_WOOL, 21, 138, 145},
            {Material.PURPLE_WOOL, 122, 42, 173}, {Material.BLUE_WOOL, 53, 57, 157},
            {Material.BROWN_WOOL, 114, 72, 41}, {Material.GREEN_WOOL, 85, 110, 28},
            {Material.RED_WOOL, 161, 39, 35}, {Material.BLACK_WOOL, 21, 21, 26},
            {Material.TERRACOTTA, 152, 94, 68}, {Material.WHITE_TERRACOTTA, 210, 178, 161},
            {Material.ORANGE_TERRACOTTA, 162, 84, 38}, {Material.MAGENTA_TERRACOTTA, 150, 88, 109},
            {Material.LIGHT_BLUE_TERRACOTTA, 113, 109, 138}, {Material.YELLOW_TERRACOTTA, 186, 133, 35},
            {Material.LIME_TERRACOTTA, 104, 118, 53}, {Material.PINK_TERRACOTTA, 162, 78, 79},
            {Material.GRAY_TERRACOTTA, 58, 42, 36}, {Material.LIGHT_GRAY_TERRACOTTA, 135, 107, 98},
            {Material.CYAN_TERRACOTTA, 87, 91, 91}, {Material.PURPLE_TERRACOTTA, 118, 70, 86},
            {Material.BLUE_TERRACOTTA, 74, 60, 91}, {Material.BROWN_TERRACOTTA, 77, 51, 36},
            {Material.GREEN_TERRACOTTA, 76, 83, 42}, {Material.RED_TERRACOTTA, 143, 61, 47},
            {Material.BLACK_TERRACOTTA, 37, 23, 17},
            {Material.STONE, 126, 126, 126}, {Material.SMOOTH_STONE, 159, 159, 159},
            {Material.DEEPSLATE, 80, 80, 82}, {Material.ANDESITE, 136, 136, 137},
            {Material.DIORITE, 189, 188, 189}, {Material.GRANITE, 149, 103, 86},
            {Material.OAK_PLANKS, 162, 131, 79}, {Material.SPRUCE_PLANKS, 115, 85, 49},
            {Material.BIRCH_PLANKS, 192, 175, 121}, {Material.JUNGLE_PLANKS, 160, 115, 81},
            {Material.ACACIA_PLANKS, 168, 90, 50}, {Material.DARK_OAK_PLANKS, 67, 43, 20},
            {Material.CHERRY_PLANKS, 226, 178, 172}, {Material.CRIMSON_PLANKS, 101, 49, 71},
            {Material.WARPED_PLANKS, 43, 105, 99}, {Material.SANDSTONE, 216, 203, 155},
            {Material.RED_SANDSTONE, 186, 99, 29}, {Material.BRICKS, 151, 98, 83},
            {Material.QUARTZ_BLOCK, 236, 230, 223}, {Material.OBSIDIAN, 15, 11, 25},
            {Material.GOLD_BLOCK, 246, 208, 62}, {Material.IRON_BLOCK, 220, 220, 220},
            {Material.DIAMOND_BLOCK, 98, 237, 228}, {Material.EMERALD_BLOCK, 42, 203, 88},
            {Material.LAPIS_BLOCK, 31, 67, 140}, {Material.REDSTONE_BLOCK, 175, 24, 5},
            {Material.SNOW_BLOCK, 249, 254, 254}, {Material.CLAY, 160, 166, 179},
            {Material.MOSS_BLOCK, 89, 110, 45}, {Material.GLOWSTONE, 172, 131, 84},
            {Material.SEA_LANTERN, 172, 199, 190}, {Material.HAY_BLOCK, 166, 136, 38},
            {Material.PUMPKIN, 198, 118, 24}, {Material.PACKED_ICE, 141, 180, 250},
            {Material.NETHER_BRICKS, 44, 21, 26}, {Material.END_STONE, 219, 222, 158},
            {Material.PURPUR_BLOCK, 169, 125, 169}, {Material.PRISMARINE, 99, 156, 151},
            {Material.BLACKSTONE, 42, 36, 41}, {Material.MUD_BRICKS, 137, 104, 79},
            {Material.COPPER_BLOCK, 192, 107, 79},
    };

    private BlockPalette() {
    }

    /** The closest full vanilla block to this color (0xRRGGBB). */
    public static Material closest(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        Material best = Material.WHITE_CONCRETE;
        double bestDistance = Double.MAX_VALUE;
        for (Object[] block : BLOCKS) {
            double distance = distance(r, g, b, (int) block[1], (int) block[2], (int) block[3]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = (Material) block[0];
            }
        }
        return best;
    }

    // "redmean" distance, a cheap way to get closer to how eyes see color then plain rgb
    private static double distance(int r1, int g1, int b1, int r2, int g2, int b2) {
        double mean = (r1 + r2) / 2.0;
        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        return (2 + mean / 256) * dr * dr + 4 * dg * dg + (2 + (255 - mean) / 256) * db * db;
    }
}
