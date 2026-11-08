package org.jan1k.displaymotion.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class MenuTest {

    @Test
    void fullRowsAreCenteredAndGoDownFromTheTop() {
        assertArrayEquals(new float[]{-1f, 1f}, Menu.cell(0, 6, 3), 1e-6f);
        assertArrayEquals(new float[]{0f, 1f}, Menu.cell(1, 6, 3), 1e-6f);
        assertArrayEquals(new float[]{1f, 1f}, Menu.cell(2, 6, 3), 1e-6f);
        assertArrayEquals(new float[]{-1f, 0f}, Menu.cell(3, 6, 3), 1e-6f);
        assertArrayEquals(new float[]{1f, 0f}, Menu.cell(5, 6, 3), 1e-6f);
    }

    @Test
    void lastRowThatIsNotFullIsCentered() {
        assertArrayEquals(new float[]{-0.5f, 0f}, Menu.cell(3, 5, 3), 1e-6f);
        assertArrayEquals(new float[]{0.5f, 0f}, Menu.cell(4, 5, 3), 1e-6f);
        assertArrayEquals(new float[]{0f, 0f}, Menu.cell(6, 7, 3), 1e-6f);
    }

    @Test
    void fewerButtonsThanColumnsMakeOneCenteredRow() {
        assertArrayEquals(new float[]{-0.5f, 0f}, Menu.cell(0, 2, 5), 1e-6f);
        assertArrayEquals(new float[]{0.5f, 0f}, Menu.cell(1, 2, 5), 1e-6f);
        assertArrayEquals(new float[]{0f, 0f}, Menu.cell(0, 1, 3), 1e-6f);
    }
}
