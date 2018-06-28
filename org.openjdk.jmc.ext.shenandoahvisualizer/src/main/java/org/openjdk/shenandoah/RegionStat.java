package org.openjdk.shenandoah;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;

import java.util.BitSet;
import java.util.EnumSet;

import static org.openjdk.shenandoah.Colors.*;

public class RegionStat {

    private static final int PERCENT_MASK = 0x7f;
    private static final int FLAGS_MASK   = 0x3f;

    private static final int USED_SHIFT   = 0;
    private static final int LIVE_SHIFT   = 7;
    private static final int TLAB_SHIFT   = 14;
    private static final int GCLAB_SHIFT  = 21;
    private static final int SHARED_SHIFT = 28;
    private static final int FLAGS_SHIFT  = 58;

    private final RegionState state;
    private final BitSet incoming;
    private final float liveLvl;
    private final float usedLvl;
    private final float tlabLvl;
    private final float gclabLvl;
    private final float sharedLvl;

    public RegionStat(float usedLvl, float liveLvl, float tlabLvl, float gclabLvl, float sharedLvl, RegionState state) {
        this.incoming = null;
        this.usedLvl = usedLvl;
        this.liveLvl = liveLvl;
        this.tlabLvl = tlabLvl;
        this.gclabLvl = gclabLvl;
        this.sharedLvl = sharedLvl;
        this.state = state;
    }


    public RegionStat(long data, String matrix) {
        usedLvl  = ((data >>> USED_SHIFT)  & PERCENT_MASK) / 100F;
        liveLvl  = ((data >>> LIVE_SHIFT)  & PERCENT_MASK) / 100F;
        tlabLvl  = ((data >>> TLAB_SHIFT)  & PERCENT_MASK) / 100F;
        gclabLvl = ((data >>> GCLAB_SHIFT) & PERCENT_MASK) / 100F;
        sharedLvl = ((data >>> SHARED_SHIFT) & PERCENT_MASK) / 100F;

        long stat = (data >>> FLAGS_SHIFT) & FLAGS_MASK;
        state = RegionState.fromOrdinal((int) stat);

        if (!matrix.isEmpty()) {
            this.incoming = new BitSet();
            int idx = 0;
            for (char c : matrix.toCharArray()) {
                c = (char) (c - 32);
                incoming.set(idx++, (c & (1 << 0)) > 0);
                incoming.set(idx++, (c & (1 << 1)) > 0);
                incoming.set(idx++, (c & (1 << 2)) > 0);
                incoming.set(idx++, (c & (1 << 3)) > 0);
                incoming.set(idx++, (c & (1 << 4)) > 0);
                incoming.set(idx++, (c & (1 << 5)) > 0);
            }
        } else {
            this.incoming = null;
        }
    }

    private org.eclipse.swt.graphics.Color selectLive(RegionState s) {
        switch (s) {
            case CSET:
                return LIVE_CSET;
            case HUMONGOUS:
                return LIVE_HUMONGOUS;
            case PINNED_HUMONGOUS:
                return LIVE_PINNED_HUMONGOUS;
            case REGULAR:
                return LIVE_REGULAR;
            case TRASH:
                return LIVE_TRASH;
            case PINNED:
                return LIVE_PINNED;
            case PINNED_CSET:
                return LIVE_PINNED_CSET;
            case EMPTY_COMMITTED:
            case EMPTY_UNCOMMITTED:
                return LIVE_EMPTY;
            default:
                return DEFAULT;
        }
    }

    public void render(GC g, int x, int y, int width, int height) {
        g.setBackground(DEFAULT);
        g.fillRectangle(x, y, width, height);

        int usedWidth = (int) (width * usedLvl);
        g.setBackground(USED);
        g.fillRectangle(x, y, usedWidth, height);

        if (state == RegionState.REGULAR) {
            if (gclabLvl > 0 || tlabLvl > 0 || sharedLvl > 0) {
                int sharedWidth = (int) (width * liveLvl * sharedLvl);
                int tlabWidth = (int) (width * liveLvl * tlabLvl);
                int gclabWidth = (int) (width * liveLvl * gclabLvl);

                int h = height;
                int ly = y + (height - h);
                int lx = x;

                g.setBackground(TLAB_ALLOC);
                g.fillRectangle(lx, ly, tlabWidth, h);
                g.setForeground(TLAB_ALLOC_BORDER);
                g.drawRectangle(lx, ly, tlabWidth, h);

                lx += tlabWidth;
                g.setBackground(GCLAB_ALLOC);
                g.fillRectangle(lx, ly, gclabWidth, h);
                g.setForeground(GCLAB_ALLOC_BORDER);
                g.drawRectangle(lx, ly, gclabWidth, h);

                lx += gclabWidth;
                g.setBackground(SHARED_ALLOC);
                g.fillRectangle(lx, ly, sharedWidth, h);
                g.setForeground(SHARED_ALLOC_BORDER);
                g.drawRectangle(lx, ly, sharedWidth, h);
            }
        } else {
            int liveWidth = (int) (width * liveLvl);
            g.setBackground(selectLive(state));
            g.fillRectangle(x, y, liveWidth, height);

            g.setForeground	(LIVE_BORDER);
            g.drawLine(x + liveWidth, y, x + liveWidth, y + height);
        }


        if (state == RegionState.TRASH) {
        	g.setForeground(g.getDevice().getSystemColor(SWT.COLOR_BLACK));
            g.drawLine(x, y, x + width, y + height);
            g.drawLine(x, y + height, x + width, y);
        }

        if (state == RegionState.EMPTY_UNCOMMITTED) {
            g.setForeground(BORDER);
            for (int t = 0; t < 3; t++) {
                int off = width * t / 3;
                g.drawLine(x, y + off, x + off, y);
                g.drawLine(x + off, y + height, x + width, y + off);
            }

        }

        g.setForeground(Colors.BORDER);
        g.drawRectangle(x, y, width, height);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RegionStat that = (RegionStat) o;

        if (Float.compare(that.liveLvl, liveLvl) != 0) return false;
        if (Float.compare(that.usedLvl, usedLvl) != 0) return false;
        if (Float.compare(that.tlabLvl, tlabLvl) != 0) return false;
        if (Float.compare(that.gclabLvl, gclabLvl) != 0) return false;
        if (!state.equals(that.state)) return false;
        return incoming != null ? incoming.equals(that.incoming) : that.incoming == null;
    }

    @Override
    public int hashCode() {
        int result = state.hashCode();
        result = 31 * result + (incoming != null ? incoming.hashCode() : 0);
        result = 31 * result + (liveLvl != +0.0f ? Float.floatToIntBits(liveLvl) : 0);
        result = 31 * result + (usedLvl != +0.0f ? Float.floatToIntBits(usedLvl) : 0);
        result = 31 * result + (tlabLvl != +0.0f ? Float.floatToIntBits(tlabLvl) : 0);
        result = 31 * result + (gclabLvl != +0.0f ? Float.floatToIntBits(gclabLvl) : 0);
        return result;
    }

    public float live() {
        return liveLvl;
    }

    public float used() {
        return usedLvl;
    }

    public float tlabAllocs() {
        return tlabLvl;
    }

    public float gclabAllocs() {
        return gclabLvl;
    }

    public float sharedAllocs() {
        return sharedLvl;
    }

    public RegionState state() {
        return state;
    }

    public BitSet incoming() {
        return incoming;
    }

}
