package org.jan1k.displaymotion;

/**
 * A named mark at one tick of an animation. Add it with {@link Animation#event(int, String)}.
 * @param tick ticks from the start of the animation
 * @param name the name your code gets when the tick is reached
 */
public record AnimationEvent(int tick, String name) {
}
