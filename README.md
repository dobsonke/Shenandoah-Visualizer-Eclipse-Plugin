Run:
 1. Start a JVM running Shenandoah by using -XX:+UseShenandoahGC with these additional flags:
     -XX:+UsePerfData -XX:+UnlockExperimentalVMOptions -XX:+ShenandoahRegionSampling


 2. Import this project as an "Existing Project from Workspace" and import Java Mission Control into eclipse. For instructions on importing JMC into Eclipse visit hirt.se for a tutorial

 3. Right click on the project org.openjdk.jmc.ext.shenandoahvisualizer and select Build Path->Configure Build Path. Go to the Libraries tab and if you don't see tools.jar or see tools.jar (missing) then select Add External Jar and add your local version of tools.jar and remove the missing jar file.
		
 4. Attach Visualizer:
  Select the JMC RCP plugins run config from Run -> Run Configurations in Eclipse which is a run config for Mission Control. It should run org.openjdk.jmc.rcp.application.product. Go to the arguments tab in Eclipse for the run config and add -Xbootclasspath/p:<path-to-tools.jar>. 
    tools.jar is usually at $JAVA_HOME/lib/tools.jar

 5. Go to the plugins tab in the Run Configurations for the same run config and select Add Plugins from the sidebar. Search for shenandoahvisualizer and select it.

 6. Apply and run it

 7. Once JMC is running select a JVM running Shenandoah then go the Window->Show View->Other. Shenandoah Visualizer should be found under the Mission Control folder.
 8. You can switch between visualizing different JVM's just by selecting them in the JVM Browser, a message will be displayed if the JVM is not running Shenandoah

 
