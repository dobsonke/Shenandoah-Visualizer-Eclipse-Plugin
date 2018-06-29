/*
 * Copyright (c) 2016, Red Hat, Inc. and/or its affiliates.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
package org.openjdk.jmc.ext.shenandoahvisualizer;

import org.eclipse.swt.*;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Event;
import java.util.*;
import org.openjdk.jmc.ext.shenandoahvisualizer.Colors.*;
import static org.openjdk.jmc.ext.shenandoahvisualizer.RegionState.*;

class ShenandoahVisualizer {

	private static final int INITIAL_WIDTH = 1000;
	private static final int INITIAL_HEIGHT = 800;

	public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("missing VM identifier");
            System.exit(-1);
        }
        Display display = new Display();
        Shell shell = new Shell(display);
        shell.setText("Shenandoah Visualizer");
        shell.setLayout(new GridLayout(1, false));
        shell.setSize(INITIAL_WIDTH, INITIAL_HEIGHT);
        
        Group outerGroup = new Group(shell, SWT.NONE);
        GridLayout grid = new GridLayout();
        grid.numColumns = 3;	
        grid.makeColumnsEqualWidth = true;
        GridData groupData = new GridData(SWT.FILL, SWT.FILL, true, true);
        outerGroup.setLayout(grid);
        outerGroup.setLayoutData(groupData);
        outerGroup.setBounds(shell.getClientArea());
    	Colors.device = display;

    	DataProvider data = new DataProvider("local://2654");

        Render render = new Render(data, outerGroup, display);

        Canvas graphPanel = new Canvas(outerGroup, SWT.NONE);
        GridData graphData = new GridData(GridData.FILL ,GridData.BEGINNING, true, false);
        graphData.horizontalSpan = 2;
        graphData.heightHint = 200;
        graphPanel.setLayoutData(graphData);
        graphPanel.addPaintListener(new PaintListener() {
            public void paintControl(PaintEvent e) {
                render.renderGraph(e.gc);
            }
        });
        
        Canvas statusPanel = new Canvas(outerGroup, SWT.NONE);
        GridData statusData = new GridData(GridData.FILL,GridData.FILL, false, false);

        statusPanel.setLayoutData(statusData);
        statusPanel.addPaintListener(new PaintListener() {
            public void paintControl(PaintEvent e) {
                render.renderStats(e.gc);
            }
        });
        Canvas regionsPanel = new Canvas(outerGroup, SWT.NONE);
        GridData regionsData = new GridData(GridData.FILL,GridData.FILL, true, true);
        regionsData.horizontalSpan = 2;
        regionsData.verticalSpan = 2;
        regionsPanel.setLayoutData(regionsData);
        regionsPanel.addPaintListener(new PaintListener() {
            public void paintControl(PaintEvent e) {
                render.renderRegions(e.gc);
            }
        });
        
        
        Canvas legendPanel = new Canvas(outerGroup, SWT.NONE); 
        GridData legendData = new GridData(GridData.FILL ,GridData.FILL, true, true);
        legendData.verticalSpan = 2;	
        legendPanel.setLayoutData(legendData);
        legendPanel.addPaintListener(new PaintListener() {
            public void paintControl(PaintEvent e) {
             	render.renderLegend(e.gc);
            }
        });
        
        graphPanel.addListener (SWT.Resize,  new Listener () {
            public void handleEvent (Event ev) {
                render.notifyGraphResized(graphPanel.getBounds().width, graphPanel.getBounds().height);

            }
          });
        
        regionsPanel.addListener (SWT.Resize,  new Listener () {
            public void handleEvent (Event ev) {
            	render.notifyRegionResized(regionsPanel.getBounds().width, regionsPanel.getBounds().height);
            }
          });
        
        display.timerExec (100, render);
       
        shell.open();
    	while (!shell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
    	display.dispose();
    }


	public static class Render implements Runnable {
		public static final int LINE = 20;

		final DataProvider data;
		final Group group;
		final Display display;
		int regionWidth, regionHeight;
		int graphWidth, graphHeight;

		final LinkedList<SnapshotView> lastSnapshots;
		volatile Snapshot snapshot;

		public Render(DataProvider data, Group outerGroup, Display display) {
			this.data = data;
			this.group = outerGroup;
			this.display = display;
			this.regionHeight = INITIAL_HEIGHT;
			this.regionWidth = INITIAL_WIDTH;
			this.graphWidth =INITIAL_WIDTH;
			this.graphHeight = INITIAL_HEIGHT;
			this.lastSnapshots = new LinkedList<>();
			this.snapshot = data.snapshot();
		}

		@Override
		public synchronized void run() {
			Snapshot cur = data.snapshot();
			if (!cur.equals(snapshot)) {
				snapshot = cur;
				lastSnapshots.add(new SnapshotView(cur));
				if (lastSnapshots.size() > graphWidth) {
					lastSnapshots.removeFirst();
				}
				if(!group.isDisposed()) {
					group.redraw();
					display.timerExec(100, this);
				}
			}
		}

		public synchronized void renderGraph(GC g) {
			if (lastSnapshots.size() < 2)
				return;

			g.setBackground(g.getDevice().getSystemColor(SWT.COLOR_BLACK));

			g.fillRectangle(0, 0, graphWidth, graphHeight);

			double stepY = 1D * graphHeight / snapshot.total();
			long firstTime = lastSnapshots.getFirst().time();
			long lastTime = lastSnapshots.getLast().time();
			double stepX = 1D * Math.min(lastSnapshots.size(), graphWidth) / (lastTime - firstTime);
			for (SnapshotView s : lastSnapshots) {
				int x = (int) Math.round((s.time() - firstTime) * stepX);

				switch (s.phase()) {
				case IDLE:
					g.setForeground(Colors.TIMELINE_IDLE);
					break;
				case MARKING:
					g.setForeground(Colors.TIMELINE_MARK);
					break;
				case EVACUATING:
					g.setForeground(Colors.TIMELINE_EVACUATING);
					break;
				case UPDATE_REFS:
					g.setForeground(Colors.TIMELINE_UPDATEREFS);
					break;
				default:
					g.setForeground(g.getDevice().getSystemColor(SWT.COLOR_BLACK));
				}
				g.drawRectangle(x, 0, 1, graphHeight);

				g.setForeground(Colors.LIVE_COMMITTED);
				g.drawRectangle(x, (int) Math.round(graphHeight - s.committed() * stepY), 1, 1);
				g.setForeground(Colors.USED);
				g.drawRectangle(x, (int) Math.round(graphHeight - s.used() * stepY), 1, 1);
				g.setForeground(Colors.LIVE_HUMONGOUS);
				g.drawRectangle(x, (int) Math.round(graphHeight - s.humongous() * stepY), 1, 1);
				g.setForeground(Colors.LIVE_REGULAR);
				g.drawRectangle(x, (int) Math.round(graphHeight - s.live() * stepY), 1, 1);
				g.setForeground(Colors.LIVE_CSET);
				g.drawRectangle(x, (int) Math.round(graphHeight - s.collectionSet() * stepY), 1, 1);
				g.setForeground(Colors.LIVE_TRASH);
				g.drawRectangle(x, (int) Math.round(graphHeight - s.trash() * stepY), 1, 1);
			}
		}

		public synchronized static void renderLegend(GC g) {
			final int sqSize = LINE;

			Map<String, RegionStat> items = new LinkedHashMap<>();

			items.put("Empty Uncommitted", new RegionStat(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, EMPTY_UNCOMMITTED));

			items.put("Empty Committed", new RegionStat(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, EMPTY_COMMITTED));

			items.put("1/2 Used", new RegionStat(0.5f, 0.0f, 0.0f, 0.0f, 0.0f, REGULAR));

			items.put("Fully Used", new RegionStat(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, REGULAR));

			items.put("Fully Used, Trash", new RegionStat(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, TRASH));

			items.put("Fully Live, 100% TLAB Allocs", new RegionStat(1.0f, 1.0f, 1.0f, 0.0f, 0.0f, REGULAR));

			items.put("Fully Live, 100% GCLAB Allocs", new RegionStat(1.0f, 1.0f, 0.0f, 1.0f, 0.0f, REGULAR));

			items.put("Fully Live, 100% Shared Allocs", new RegionStat(1.0f, 1.0f, 0.0f, 0.0f, 1.0f, REGULAR));

			items.put("Fully Live, 50%/50% TLAB/GCLAB Allocs", new RegionStat(1.0f, 1.0f, 0.5f, 0.5f, 0.0f, REGULAR));

			items.put("Fully Live, 33%/33%/33% T/GC/S Allocs",
					new RegionStat(1.0f, 1.0f, 1f / 3, 1f / 3, 1f / 3, REGULAR));

			items.put("Fully Live Humongous", new RegionStat(1.0f, 1.0f, 0.0f, 0.0f, 0.0f, HUMONGOUS));

			items.put("Fully Live Humongous + Pinned", new RegionStat(1.0f, 1.0f, 0.0f, 0.0f, 0.0f, PINNED_HUMONGOUS));

			items.put("1/3 Live", new RegionStat(1.0f, 0.3f, 0.0f, 0.0f, 0.0f, REGULAR));

			items.put("1/3 Live + Collection Set", new RegionStat(1.0f, 0.3f, 0.0f, 0.0f, 0.0f, CSET));

			items.put("1/3 Live + Pinned", new RegionStat(1.0f, 0.3f, 0.0f, 0.0f, 0.0f, PINNED));

			items.put("1/3 Live + Pinned CSet", new RegionStat(1.0f, 0.3f, 0.0f, 0.0f, 0.0f, PINNED_CSET));

			int i = 1;


			for (String key : items.keySet()) {
				int y = (int) (i * sqSize * 1.5);
				items.get(key).render(g, 0, y, sqSize, sqSize);
				g.setForeground(g.getDevice().getSystemColor(SWT.COLOR_BLACK));
				g.drawString(key, (int) (sqSize * 1.5), (int) (y+sqSize*0.1), true);
				i++;
			}
		}

		public synchronized void renderRegions(GC g) {
			int area = regionWidth * regionHeight;
			int sqSize = Math.max(1, (int) Math.sqrt(1D * area / snapshot.regionCount()));
			int cols = regionWidth / sqSize;
			int cellSize = sqSize - 2;

			for (int i = 0; i < snapshot.regionCount(); i++) {
				int rectx = (i % cols) * sqSize;
				int recty = (i / cols) * sqSize;

				RegionStat s = snapshot.get(i);
				s.render(g, rectx, recty, cellSize, cellSize);
			}

			for (int f = 0; f < snapshot.regionCount(); f++) {
				RegionStat s = snapshot.get(f);
				BitSet bs = s.incoming();
				if (bs != null) {
					for (int t = 0; t < snapshot.regionCount(); t++) {
						if (bs.get(t)) {
							int f_rectx = (int) ((f % cols + 0.5) * sqSize);
							int f_recty = (int) ((f / cols + 0.5) * sqSize);
							int t_rectx = (int) ((t % cols + 0.5) * sqSize);
							int t_recty = (int) ((t / cols + 0.5) * sqSize);

							g.setBackground(Colors.GCLAB_ALLOC);
							g.setAlpha(20);
							g.drawLine(f_rectx, f_recty, t_rectx, t_recty);
						}
					}
				}
			}
		}

		public synchronized void renderStats(GC g) {
			String status = "";
			switch (snapshot.phase()) {
			case IDLE:
				status += " (idle)";
				break;
			case MARKING:
				status += " (marking)";
				break;
			case EVACUATING:
				status += " (evacuating)";
				break;
			case UPDATE_REFS:
				status += " (updating refs)";
				break;
			}
			
			g.setForeground(g.getDevice().getSystemColor(SWT.COLOR_BLACK));
			g.drawText("Time: " + (snapshot.time() / 1024 / 1024) + " ms", 0, 0 * LINE, true);
			g.drawText("Status: " + status, 0, 1 * LINE);
			g.drawText("Total: " + (snapshot.total()) + " KB", 0, 2 * LINE, true);
			g.drawText("Used: " + (snapshot.used()) + " KB", 0, 3 * LINE, true);
			g.drawText("Live: " + (snapshot.live()) + " KB", 0, 4 * LINE, true);
		}

		public synchronized void notifyRegionResized(int width, int height) {
				this.regionWidth = width;
				this.regionHeight = height;

		}

		public synchronized void notifyGraphResized(int width, int height) {
				this.graphWidth = width;
				this.graphHeight = height;
	
		}
	}
}
