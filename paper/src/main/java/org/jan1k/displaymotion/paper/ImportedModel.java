package org.jan1k.displaymotion.paper;

import org.bukkit.Location;
import org.jan1k.displaymotion.paper.bdengine.BdengineModel;
import org.jan1k.displaymotion.paper.blockbench.BlockbenchModel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** A model made in an editor. Load it once, spawn it as often as you want. */
public interface ImportedModel {

    /** Loads a {@code .bbmodel} (Blockbench) or {@code .bdengine} (BDEngine) file, picked by the file name. */
    static ImportedModel load(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".bbmodel")) {
            return BlockbenchModel.load(file);
        }
        if (name.endsWith(".bdengine")) {
            return BdengineModel.load(file);
        }
        throw new IllegalArgumentException("only .bbmodel and .bdengine files work, got " + file.getFileName());
    }

    /** Spawns it at its real size. */
    MotionDisplay spawn(DisplayMotion motion, Location at);

    /** Spawns it bigger or smaller. 0.5 is half size. */
    MotionDisplay spawn(DisplayMotion motion, Location at, float scale);

    /** Plays one of its animations on a display spawned from this model. */
    default void play(MotionDisplay display, String animation) {
        play(display, animation, 0);
    }

    /** Same, but fades into it from what plays now over {@code fadeTicks}, see {@link MotionDisplay#crossfade(org.jan1k.displaymotion.Animation, int)}. */
    void play(MotionDisplay display, String animation, int fadeTicks);

    /** Names of the animations in the file. */
    List<String> animations();

    /** Names of the parts, the names {@link MotionDisplay#play(String, org.jan1k.displaymotion.Animation)} takes. */
    List<String> parts();
}
