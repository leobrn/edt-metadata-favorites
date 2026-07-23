/**
 * Copyright (C) 2026
 */
package edt.metadata.favorites;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;


public class PinnedOnlyFilter extends ViewerFilter
{
    public static final String ID = "edt.metadata.favorites.filter";


    private final Map<Object, Boolean> descendantCache = new IdentityHashMap<>();


    private boolean descendantCacheClearScheduled;


    private final Map<String, Boolean> hasPinnedObjectsCache = new HashMap<>();


    private int cachedModCount = -1;

    @Override
    public boolean select(Viewer viewer, Object parentElement, Object element)
    {
        PinStore store = Activator.getDefault().getPinStore();
        syncModCount(store);

        String projectName = MetadataPinSupport.getProjectName(element);
        if (MetadataPinSupport.isProjectNode(element))
        {
            if (projectName != null && store.isProjectPinned(projectName))
            {
                return true;
            }
        }
        else
        {
            if (projectName != null && store.isProjectPinned(projectName)
                && !hasPinnedObjects(store, projectName))
            {
                return true;
            }

            String uuid = MetadataPinSupport.getUuid(element);
            if (uuid != null && projectName != null
                && store.isObjectEffectivelyPinned(projectName, MetadataPinSupport.getUuidPath(element)))
            {
                return true;
            }
        }

        if (viewer instanceof AbstractTreeViewer treeViewer)
        {
            return hasPinnedDescendant(treeViewer, element);
        }

        return false;
    }


    private void syncModCount(PinStore store)
    {
        int currentModCount = store.getModCount();
        if (currentModCount != cachedModCount)
        {
            cachedModCount = currentModCount;
            hasPinnedObjectsCache.clear();
            descendantCache.clear();
        }
    }


    private boolean hasPinnedObjects(PinStore store, String projectName)
    {
        Boolean value = hasPinnedObjectsCache.get(projectName);
        if (value == null)
        {
            value = store.hasPinnedObjects(projectName);
            hasPinnedObjectsCache.put(projectName, value);
        }
        return value;
    }

    private boolean hasPinnedDescendant(AbstractTreeViewer viewer, Object element)
    {
        Boolean cached = descendantCache.get(element);
        if (cached != null)
        {
            return cached;
        }

        boolean cacheable = ensureDescendantCacheClearScheduled(viewer);
        boolean result = computeHasPinnedDescendant(viewer, element);
        if (cacheable)
        {
            descendantCache.put(element, result);
        }
        return result;
    }


    private boolean ensureDescendantCacheClearScheduled(Viewer viewer)
    {
        if (descendantCacheClearScheduled)
        {
            return true;
        }
        Display display = displayOf(viewer);
        if (display == null)
        {
            return false;
        }
        descendantCacheClearScheduled = true;
        display.asyncExec(() -> {
            descendantCache.clear();
            descendantCacheClearScheduled = false;
        });
        return true;
    }

    private static Display displayOf(Viewer viewer)
    {
        Control control = viewer.getControl();
        if (control != null && !control.isDisposed())
        {
            return control.getDisplay();
        }
        return Display.getCurrent();
    }

    private boolean computeHasPinnedDescendant(AbstractTreeViewer viewer, Object element)
    {
        if (!(viewer.getContentProvider() instanceof ITreeContentProvider contentProvider))
        {
            return false;
        }

        Object[] children = contentProvider.getChildren(element);
        if (children == null)
        {
            return false;
        }

        PinStore store = Activator.getDefault().getPinStore();
        for (Object child : children)
        {
            String childProject = MetadataPinSupport.getProjectName(child);
            if (MetadataPinSupport.isProjectNode(child) && childProject != null
                && store.isProjectPinned(childProject))
            {
                return true;
            }

            if (!MetadataPinSupport.isProjectNode(child) && childProject != null
                && store.isProjectPinned(childProject) && !hasPinnedObjects(store, childProject))
            {
                return true;
            }

            String childUuid = MetadataPinSupport.getUuid(child);
            if (childUuid != null && childProject != null
                && store.isObjectEffectivelyPinned(childProject, MetadataPinSupport.getUuidPath(child)))
            {
                return true;
            }
            if (hasPinnedDescendant(viewer, child))
            {
                return true;
            }
        }
        return false;
    }
}
