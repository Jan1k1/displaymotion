package org.jan1k.displaymotion.paper;

/**
 * A playing animation reached one of its events.
 * @param display the display that plays the animation
 * @param name the name given to {@link org.jan1k.displaymotion.Animation#event(int, String)}
 * @param part the part the animation plays on, or {@code null} when it plays on the whole display
 */
public record DisplayEvent(MotionDisplay display, String name, String part) {
}
