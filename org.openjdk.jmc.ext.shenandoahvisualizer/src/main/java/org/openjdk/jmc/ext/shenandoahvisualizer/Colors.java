package org.openjdk.jmc.ext.shenandoahvisualizer;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.graphics.Color;

public class Colors {
	
	static Display device;
    static final Color TIMELINE_IDLE        = new Color(device, 0, 0, 0);
    static final Color TIMELINE_MARK        = new Color(device, 100, 100, 0);
    static final Color TIMELINE_EVACUATING  = new Color(device, 100, 0, 0);
    static final Color TIMELINE_UPDATEREFS  = new Color(device, 0, 100, 100);

    static final Color SHARED_ALLOC           = new Color(device, 0, 250, 250);
    static final Color SHARED_ALLOC_BORDER    = new Color(device, 0, 191, 190);
    static final Color TLAB_ALLOC           = new Color(device, 0, 200, 0);
    static final Color TLAB_ALLOC_BORDER    = new Color(device, 0, 100, 0);
    static final Color GCLAB_ALLOC          = new Color(device, 185, 0, 250);
    static final Color GCLAB_ALLOC_BORDER   = new Color(device, 118, 0, 160);

    static final Color USED                 = new Color(device, 220, 220, 220);
    static final Color DEFAULT				= new Color(device, 255, 255, 255);
    static final Color LIVE_COMMITTED       = new Color(device, 150, 150, 150);
    static final Color LIVE_REGULAR         = new Color(device, 0, 200, 0);
    static final Color LIVE_HUMONGOUS       = new Color(device, 250, 100, 0);
    static final Color LIVE_PINNED_HUMONGOUS = new Color(device, 255, 0, 0);
    static final Color LIVE_CSET            = new Color(device, 250, 250, 0);
    static final Color LIVE_TRASH           = new Color(device, 100, 100, 100);
    static final Color LIVE_PINNED          = new Color(device, 255, 0, 0);
    static final Color LIVE_PINNED_CSET     = new Color(device, 255, 120, 0);
    static final Color LIVE_EMPTY           = new Color(device, 255, 255, 255);

    static final Color LIVE_BORDER          = new Color(device, 0, 100, 0);
    static final Color BORDER               = new Color(device, 150, 150, 150);
    
}
