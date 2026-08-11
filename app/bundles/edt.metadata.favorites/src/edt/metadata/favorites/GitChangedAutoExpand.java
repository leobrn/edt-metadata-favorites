/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.navigator.CommonViewer;


final class GitChangedAutoExpand
{
    private static final int EXPAND_LEVELS = 3;

    private static final Map<CommonViewer, Object[]> pendingRoots = new WeakHashMap<>();

    private GitChangedAutoExpand()
    {
    }

    static Object[] rootElements(CommonViewer viewer)
    {
        return Arrays.stream(viewer.getTree().getItems())
            .map(item -> item.getData())
            .toArray();
    }

    static void request(CommonViewer viewer, Object[] roots)
    {
        Control control = viewer.getControl();
        if (control == null || control.isDisposed())
        {
            return;
        }
        pendingRoots.put(viewer, roots.clone());
        control.getDisplay().asyncExec(() -> afterViewerRefresh(viewer));
    }

    static void cancel(CommonViewer viewer)
    {
        pendingRoots.remove(viewer);
    }

    static void afterViewerRefresh(CommonViewer viewer)
    {
        Object[] roots = pendingRoots.get(viewer);
        if (roots == null)
        {
            return;
        }
        Control control = viewer.getControl();
        if (control == null || control.isDisposed()
            || !NavigatorAccess.isFilterActive(viewer, GitChangedOnlyFilter.class))
        {
            pendingRoots.remove(viewer);
            return;
        }

        GitChangeCache changes = Activator.getDefault().getGitChangeCache();
        List<Object> rootsToExpand = new ArrayList<>();
        int changedObjects = 0;
        for (Object root : roots)
        {
            GitChangeState state = changes.getState(root);
            if (state == GitChangeState.UNKNOWN)
            {
                return;
            }
            if (!changes.isSnapshotCurrent(root))
            {
                return;
            }
            if (state != GitChangeState.DIRTY)
            {
                continue;
            }

            int remaining = FavoriteUiLimits.MAX_AUTO_EXPANDED_OBJECTS - changedObjects;
            int projectChangedObjects = changes.countChangedObjects(root, remaining);
            changedObjects += projectChangedObjects;
            if (changedObjects > FavoriteUiLimits.MAX_AUTO_EXPANDED_OBJECTS)
            {
                pendingRoots.remove(viewer);
                return;
            }
            if (projectChangedObjects > 0)
            {
                rootsToExpand.add(root);
            }
        }

        pendingRoots.remove(viewer);
        for (Object root : rootsToExpand)
        {
            viewer.expandToLevel(root, EXPAND_LEVELS);
        }
    }
}
