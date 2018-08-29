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

import javax.inject.Inject;
//import org.eclipse.jface.viewers.ISelection;
//import org.eclipse.jface.viewers.IStructuredSelection;
//import org.eclipse.jface.viewers.TreePath;
//import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.swt.*;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.IMemento;
//import org.eclipse.ui.ISelectionListener;
//import org.eclipse.ui.IViewSite;
//import org.eclipse.ui.IWorkbenchPart;
//import org.eclipse.ui.PartInitException;
//import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.openjdk.jmc.console.ui.editor.IConsolePageStateHandler;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import org.openjdk.jmc.ext.shenandoahvisualizer.Colors.*;
import org.openjdk.jmc.rjmx.IConnectionHandle;

import static org.openjdk.jmc.ext.shenandoahvisualizer.RegionState.*;

public class ShenandoahVisualizer implements IConsolePageStateHandler {
	private static final int INITIAL_WIDTH = 1000;
	private static final int INITIAL_HEIGHT = 800;
	private static int pid;
	private static final ScheduledExecutorService sched = Executors.newScheduledThreadPool(1);
	private static Render render;
	private static Image image;
	private static GC g;
	private static Map<String, RegionStat> items = new LinkedHashMap<>();

	@Inject
	protected void createPageContent(IManagedForm managedForm, IConnectionHandle connection) throws Exception {
		FormToolkit toolkit = managedForm.getToolkit();
		Composite body = managedForm.getForm().getBody();
		Rectangle size = managedForm.getForm().getClientArea();
//		managedForm.getForm().addControlListener(new ControlAdapter() {
//			public void controlResized(ControlEvent ev) {
//				body.setBounds(managedForm.getForm().getClientArea());
//			}
//		});
		toolkit.adapt(body, false, false);
		pid = connection.getServerDescriptor().getJvmInfo().getPid();
		String args = connection.getServerDescriptor().getJvmInfo().getJVMArguments();
		if (args.contains("-XX:+UsePerfData") && args.contains("-XX:+UnlockExperimentalVMOptions")
				&& args.contains("-XX:+ShenandoahRegionSampling")) {
			createVisualizer(body, size, managedForm.getForm());
		} else {
			Text error = new Text(body, SWT.READ_ONLY | SWT.CENTER);
			error.setText("Could not detect a JVM using Shenandoah");
			error.setBounds(size);
		}
	}

	public boolean saveState(IMemento memento) {
		return false;
	}

	public static class Render implements Runnable {
		public static final int LINE = 20;

		DataProvider data;
		final Group group;
		int regionWidth, regionHeight;
		int graphWidth, graphHeight;

		final LinkedList<SnapshotView> lastSnapshots;
		volatile Snapshot snapshot, previousSnapshot;

		public Render(Group outerGroup) throws Exception {
			this.data = new DataProvider("local://" + pid);
			this.group = outerGroup;
			this.regionHeight = INITIAL_HEIGHT;
			this.regionWidth = INITIAL_WIDTH;
			this.graphWidth = INITIAL_WIDTH;
			this.graphHeight = INITIAL_HEIGHT;
			this.lastSnapshots = new LinkedList<>();
			this.snapshot = data.snapshot();
			this.previousSnapshot = null;
		}

		@Override
		public synchronized void run() {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					Snapshot cur = data.snapshot();
					if (!cur.equals(snapshot)) {
						previousSnapshot = snapshot;
						snapshot = cur;
						lastSnapshots.add(new SnapshotView(cur));
						if (lastSnapshots.size() > graphWidth) {
							lastSnapshots.removeFirst();
						}
						if (!group.isDisposed()) {
							group.redraw();
							group.update();
						}
					}
				
				}
			});
		}

		public synchronized void renderGraph(GC g) {
			if (lastSnapshots.size() < 2)
				return;
			
			int pad = 10; 
			int bandHeight = (graphHeight - pad) / 2;
			double stepY = 1D * bandHeight / snapshot.total();
			
			int startDiff = graphHeight;
			int startRaw = graphHeight - bandHeight - pad;
			
			g.setBackground(g.getDevice().getSystemColor(SWT.COLOR_WHITE));
			g.fillRectangle(0, 0, graphWidth, graphHeight);

			g.setBackground(g.getDevice().getSystemColor(SWT.COLOR_BLACK));
			g.fillRectangle(0, 0, graphWidth, bandHeight);
			g.fillRectangle(0, bandHeight+ pad, graphWidth, bandHeight);

			long firstTime = lastSnapshots.getFirst().time();
			long lastTime = lastSnapshots.getLast().time();
			double stepX = 1D * Math.min(lastSnapshots.size(), graphWidth) / (lastTime - firstTime);
			for (int i = 0; i < lastSnapshots.size(); i++) {
				SnapshotView s = lastSnapshots.get(i);
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
				case TRAVERSAL:
					g.setForeground(Colors.TIMELINE_TRAVERSAL);
					break;
				default:
					//FIXME: check if this should be white
					g.setForeground(g.getDevice().getSystemColor(SWT.COLOR_WHITE));
				}
				g.drawRectangle(x, 0, 1, bandHeight);
				g.drawRectangle(x, bandHeight + pad, 1, bandHeight);

				if(s.used() != 0) {
					g.setForeground(Colors.USED);
					g.drawRectangle(x, (int) Math.round(startRaw - s.used() * stepY), 1, 1);
				}
				if (s.live() != 0) {
					g.setForeground(Colors.LIVE_REGULAR);
					g.drawRectangle(x, (int) Math.round(startRaw - s.live() * stepY), 1, 1);
				}
				if (s.collectionSet() != 0) {
					g.setForeground(Colors.LIVE_CSET);
					g.drawRectangle(x, (int) Math.round(startRaw - s.collectionSet() * stepY), 1, 1);
				}
				final int smooth = Math.min(10,  i +1);
				final int mult = 50;
				
				SnapshotView ls = lastSnapshots.get(i - smooth +1);
				g.setForeground(Colors.USED);
				g.drawRectangle(x, (int) Math.round(startDiff - (s.used() - ls.used()) * stepY * mult / smooth), 1, 1);
		
			}
		}

		public synchronized static void renderLegend(GC g) {
			final int sqSize = LINE;

			int i = 1;

			for (String key : items.keySet()) {
				int y = (int) (i * sqSize * 1.5);
				g.setAlpha(100);
				items.get(key).render(g, 0, y, sqSize, sqSize);
				g.setForeground(g.getDevice().getSystemColor(SWT.COLOR_BLACK));
				g.setAlpha(255);
				g.drawString(key, (int) (sqSize * 1.5), (int) (y + sqSize * 0.1), true);
				i++;
			}
		}

		public synchronized void renderRegions(GC g) {
			int area = regionWidth * regionHeight;
			int sqSize = Math.max(1, (int) Math.sqrt(1D * area / snapshot.regionCount()));
			int cols = regionWidth / sqSize;
			int cellSize = sqSize - 2;

			for (int i = 0; i < snapshot.regionCount(); i++) {
				
				RegionStat s = snapshot.get(i);
				RegionStat ls = previousSnapshot.get(i);
				if (s != ls){
					int rectx = (i % cols) * sqSize;
					int recty = (i / cols) * sqSize;
					s.render(g, rectx, recty, cellSize, cellSize);
				}
				else {
					System.out.println("Region " + i + "is the same");
				}
			}
			
			Color BASE = new Color (g.getDevice(), 0, 0, 0);
			
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
	
								//g.setAlpha(20);
								g.setBackground(BASE);
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
			case TRAVERSAL:
				status += " (traversal)";
				break;
			}
			
			final int K = 1024;
			
			g.setForeground(g.getDevice().getSystemColor(SWT.COLOR_BLACK));
			g.drawText("Status: " + status, 0, 1 * LINE);
			g.drawText("Total: " + (snapshot.total() / K) + " MB", 0, 2 * LINE, true);
			g.drawText("Used: " + (snapshot.used() / K) + " MB", 0, 3 * LINE, true);
			g.drawText("Live: " + (snapshot.live() / K) + " MB", 0, 4 * LINE, true);
		}

		public synchronized void notifyRegionResized(int width, int height) {
			this.regionWidth = width;
			this.regionHeight = height;

		}

		public synchronized void notifyGraphResized(int width, int height) {
			this.graphWidth = width;
			this.graphHeight = height;

		}

		public synchronized void updateDataProvider() throws Exception {
			this.data = new DataProvider("local://" + pid);
			this.snapshot = this.data.snapshot();
		}

		public synchronized boolean isDataNull() {
			if (this.data == null) {
				return true;
			}
			return false;
		}

	}

	@Override
	public void dispose() {
		g.dispose();
		image.dispose();
	}

	public void createVisualizer(Composite parent, Rectangle size, ScrolledForm form) throws Exception {
		Group outerGroup = new Group(parent, SWT.NONE);
		GridLayout grid = new GridLayout();
		grid.numColumns = 3;
		
		grid.makeColumnsEqualWidth = true;
		GridData groupData = new GridData(SWT.FILL, SWT.FILL, true, true);
		outerGroup.setLayout(grid);
		outerGroup.setLayoutData(groupData);
		outerGroup.setBounds(size);
		outerGroup.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
		outerGroup.setBackgroundMode(SWT.INHERIT_FORCE);
		image = new Image(Display.getDefault(), 1000, 1000);
		g = new GC(image);
		Colors.device = Display.getDefault();
		
		fillLegend();
		
		render = new Render(outerGroup);
		createPanels(outerGroup, form);
		sched.scheduleWithFixedDelay(render, 0, 10, MILLISECONDS);
		
	}


	public void fillLegend() {

		items.put("Empty Uncommitted", new RegionStat(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, EMPTY_UNCOMMITTED));

		items.put("Empty Committed", new RegionStat(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, EMPTY_COMMITTED));

		items.put("Trash", new RegionStat(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, TRASH));

		items.put("Fully Live, 100% TLAB Allocs", new RegionStat(1.0f, 1.0f, 1.0f, 0.0f, 0.0f, REGULAR));
		
		items.put("0% Live, 100% TLAB Allocs", new RegionStat(1.0f, 0.0f, 1.0f, 0.0f, 0.0f, REGULAR));

		items.put("Fully Live, 100% GCLAB Allocs", new RegionStat(1.0f, 1.0f, 0.0f, 1.0f, 0.0f, REGULAR));
		
		items.put("0% Live, 100% GCLAB Allocs", new RegionStat(1.0f, 0.0f, 0.0f, 1.0f, 0.0f, REGULAR));

		items.put("Fully Live, 100% Shared Allocs", new RegionStat(1.0f, 1.0f, 0.0f, 0.0f, 1.0f, REGULAR));
		
		items.put("0% Live, 100% Shared Allocs", new RegionStat(1.0f, 0.0f, 0.0f, 0.0f, 1.0f, REGULAR));

		items.put("Fully Live, 50%/50% TLAB/GCLAB Allocs", new RegionStat(1.0f, 1.0f, 0.5f, 0.5f, 0.0f, REGULAR));

		items.put("Fully Live, 33%/33%/33% T/GC/S Allocs", new RegionStat(1.0f, 1.0f, 1f / 3, 1f / 3, 1f / 3, REGULAR));

		items.put("Fully Live Humongous", new RegionStat(1.0f, 1.0f, 0.0f, 0.0f, 0.0f, HUMONGOUS));

		items.put("Fully Live Humongous + Pinned", new RegionStat(1.0f, 1.0f, 0.0f, 0.0f, 0.0f, PINNED_HUMONGOUS));

		items.put("1/3 Live + Collection Set", new RegionStat(1.0f, 1f / 3, 0.0f, 0.0f, 0.0f, CSET));

		items.put("1/3 Live + Pinned", new RegionStat(1.0f, 0.3f, 1f / 3, 0.0f, 0.0f, PINNED));

		items.put("1/3 Live + Pinned CSet", new RegionStat(1.0f, 1f / 3, 0.0f, 0.0f, 0.0f, PINNED_CSET));
	}

	public void createPanels(Composite parent, ScrolledForm form) {

		Canvas graphPanel = new Canvas(parent, SWT.NO_BACKGROUND);
		GridData graphData = new GridData(GridData.FILL, GridData.BEGINNING, true, false);
		graphData.horizontalSpan = 2;
		graphData.heightHint = 200;
		graphPanel.setLayoutData(graphData);
		graphPanel.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				render.renderGraph(e.gc);
			}
		});

		Canvas statusPanel = new Canvas(parent, SWT.NO_BACKGROUND);
		GridData statusData = new GridData(GridData.FILL, GridData.FILL, false, false);

		statusPanel.setLayoutData(statusData);
		statusPanel.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				render.renderStats(e.gc);
			}
		});
		Canvas regionsPanel = new Canvas(parent, SWT.NO_BACKGROUND);
		GridData regionsData = new GridData(GridData.FILL, GridData.FILL, true, true);
		regionsData.horizontalSpan = 2;
		regionsData.verticalSpan = 2;
		regionsPanel.setLayoutData(regionsData);
		regionsPanel.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				render.renderRegions(g);
				e.gc.drawImage(image, 0, 0, image.getBounds().width, image.getBounds().height,
						0, 0, regionsPanel.getBounds().width, regionsPanel.getBounds().height);
			}
		});
		regionsPanel.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
			g.dispose();
			image.dispose();
			}
		});

		Canvas legendPanel = new Canvas(parent, SWT.NO_BACKGROUND);
		GridData legendData = new GridData(GridData.FILL, GridData.FILL, true, true);
		legendData.verticalSpan = 2;
		legendPanel.setLayoutData(legendData);
		legendPanel.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				render.renderLegend(e.gc);
			}
		});

		/*
		 * An SWT.Resize event isn't called by resizing the form so we manually resize the
		 * outerGroup parent here to trigger a Resize event for all it's children
		 */

		form.addControlListener(new ControlAdapter() {
			public void controlResized(ControlEvent ev) {
				parent.setBounds(form.getClientArea());
			}
		});

		graphPanel.addControlListener(new ControlAdapter() {
			public void controlResized(ControlEvent ev) {
				render.notifyGraphResized(graphPanel.getBounds().width, graphPanel.getBounds().height);
			}
		});

		regionsPanel.addControlListener(new ControlAdapter() {
			public void controlResized(ControlEvent ev) {
				render.notifyRegionResized(regionsPanel.getBounds().width, regionsPanel.getBounds().height);
				g.dispose();
				image.dispose();
				image = new Image(Display.getDefault(), regionsPanel.getBounds().width, regionsPanel.getBounds().height);
				g = new GC(image);
				
			}
		});
	}
}
