/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;


public class Activator
    extends AbstractUIPlugin
{
    public static final String PLUGIN_ID = "edt.metadata.favorites"; //$NON-NLS-1$

    private static Activator plugin;

    private PinStore pinStore;

    private GitChangeCache gitChangeCache;

    private boolean stopped;

    @Override
    public synchronized void start(BundleContext context) throws Exception
    {
        super.start(context);
        stopped = false;
        plugin = this;
    }

    @Override
    public synchronized void stop(BundleContext context) throws Exception
    {
        stopped = true;
        if (gitChangeCache != null)
        {
            gitChangeCache.dispose();
            gitChangeCache = null;
        }
        plugin = null;
        super.stop(context);
    }

    public static Activator getDefault()
    {
        return plugin;
    }


    public synchronized PinStore getPinStore()
    {
        if (pinStore == null)
        {
            pinStore = new PinStore(getStateLocation());
            pinStore.pruneMissingProjects(currentWorkspaceProjectNames());
        }
        return pinStore;
    }

    synchronized GitChangeCache getGitChangeCache()
    {
        if (stopped)
        {
            throw new IllegalStateException("Plugin is stopped");
        }
        if (gitChangeCache == null)
        {
            gitChangeCache = new GitChangeCache();
        }
        return gitChangeCache;
    }

    private static Set<String> currentWorkspaceProjectNames()
    {
        Set<String> names = new HashSet<>();
        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects())
        {
            names.add(project.getName());
        }
        return names;
    }

    public static void logError(String message, Throwable throwable)
    {
        Activator current = getDefault();
        if (current != null)
        {
            current.getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, message, throwable));
        }
    }
}
